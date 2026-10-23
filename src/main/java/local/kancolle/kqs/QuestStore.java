package local.kancolle.kqs;

import java.time.Instant;
import java.util.*;

/** Token-free in-memory quest state. All methods are synchronized. */
public final class QuestStore {
    private record Quest(int id,String title,String detail,int state,int progress,Instant seen) {}
    private final Map<Integer,Quest> quests=new LinkedHashMap<>();
    private final Map<String,Instant> pages=new LinkedHashMap<>();
    private Instant lastUpdate,lastList;
    private Integer reportedAccepted;
    private Integer reportedTotal;
    private Integer reportedPages;
    private Instant lastAllTab;
    private String lastTab;
    private int acceptedEvents;

    public synchronized boolean accept(String path,Map<String,String> params,String body) {
        if(path==null||body==null||body.length()>2_000_000)return false;
        String kind=switch(path){
            case "/kcsapi/api_get_member/questlist"->"list";
            case "/kcsapi/api_req_quest/start"->"start";
            case "/kcsapi/api_req_quest/stop"->"stop";
            case "/kcsapi/api_req_quest/clearitemget"->"clear";
            default->null;
        };
        if(kind==null)return false;
        Object raw;
        try{raw=Json.parse(body.startsWith("svdata=")?body.substring(7):body);}
        catch(RuntimeException error){return false;}
        if(!(raw instanceof Map<?,?> response)||number(response.get("api_result"),0)!=1)return false;
        Object data=response.get("api_data"); Instant now=Instant.now();
        if(kind.equals("list")){
            if(!(data instanceof Map<?,?> detail)||!(detail.get("api_list") instanceof List<?> list))return false;
            int page=parseInt(params==null?null:params.get("api_page_no"),1);
            String tab=strip(params==null?"all":params.getOrDefault("api_tab_id","all"),20);
            pages.put(tab+":"+page,now);lastList=now;lastTab=tab;
            if ("0".equals(tab)) lastAllTab=now;
            Integer total=optionalNumber(detail.get("api_count"));
            if(total!=null)reportedTotal=total;
            Integer count=optionalNumber(detail.get("api_page_count"));
            if(count!=null)reportedPages=count;
            Integer reported=optionalNumber(detail.get("api_exec_count"));
            if(reported!=null)reportedAccepted=reported;
            for(Object obj:list){
                if(!(obj instanceof Map<?,?> q))continue;
                int id=number(q.get("api_no"),-1);if(id<1||id>9_999_999)continue;
                Quest old=quests.get(id);
                String title=strip(q.get("api_title"),200);
                String description=strip(q.get("api_detail"),700);
                if(title.isEmpty())title=old==null?"任務 #"+id:old.title;
                if(description.isEmpty()&&old!=null)description=old.detail;
                quests.put(id,new Quest(id,title,description,number(q.get("api_state"),old==null?1:old.state),
                    number(q.get("api_progress_flag"),old==null?0:old.progress),now));
            }
        }else{
            int id=parseInt(params==null?null:params.get("api_quest_id"),-1);
            if(id<1||id>9_999_999)return false;
            Quest prev=quests.getOrDefault(id,new Quest(id,"任務 #"+id,"",1,0,now));
            int state=kind.equals("start")?2:kind.equals("stop")?1:0;
            quests.put(id,new Quest(id,prev.title,prev.detail,state,prev.progress,now));
        }
        lastUpdate=now;acceptedEvents++;
        if(quests.size()>400)quests.values().stream().filter(q->q.state!=2&&q.state!=3)
            .min(Comparator.comparing(Quest::seen)).ifPresent(q->quests.remove(q.id));
        return true;
    }

    public synchronized Map<String,Object> captureEvidence() {
        Map<String,Object> data=new LinkedHashMap<>();
        data.put("quest_list_observed", lastList!=null);
        data.put("all_tab_observed_this_session", lastAllTab!=null);
        data.put("last_list_at", lastList==null?null:lastList.toString());
        data.put("last_all_tab_at", lastAllTab==null?null:lastAllTab.toString());
        data.put("last_tab_id", lastTab);
        data.put("api_count", reportedTotal);
        data.put("api_page_count", reportedPages);
        data.put("api_exec_count", reportedAccepted);
        data.put("observed_page_keys", List.copyOf(pages.keySet()));
        data.put("observed_quest_events", acceptedEvents);
        return data;
    }
    public synchronized int activeCount(){
        return (int)quests.values().stream().filter(q->q.state==2||q.state==3).count();
    }
    public synchronized int acceptedEvents(){return acceptedEvents;}
    public synchronized String status(){
        long completed=quests.values().stream().filter(q->q.state==3).count();
        return "受注/達成 "+activeCount()+"（達成"+completed+"） | 取得ページ "+pages.size()
            +" | 最終取得 "+(lastUpdate==null?"なし":lastUpdate.toString());
    }
    public synchronized String prompt(boolean includeUnaccepted){
        List<Quest> active=quests.values().stream().filter(q->q.state==2||q.state==3)
            .sorted(Comparator.comparingInt(Quest::id)).toList();
        StringBuilder b=new StringBuilder();
        b.append("艦これの任務受注状況です。任務の優先順位と並行消化を整理してください。期限付き・恒常入手困難な報酬を優先し、資源・戦果中心の報酬は低優先としてください。条件はwikiwiki.jp/kancolle/とzekamashi.netで確認してください。\n\n");
        b.append("取得日時: ").append(lastUpdate==null?"未取得":lastUpdate).append('\n');
        b.append("任務一覧の最終取得: ").append(lastList==null?"未取得":lastList).append('\n');
        b.append("表示済みページ: ").append(pages.size()).append(" / 受注・達成（未受領）: ").append(active.size());
        if(reportedAccepted!=null)b.append(" / API受注数: ").append(reportedAccepted);
        b.append("\n注意: 任務一覧を表示していないページ・過去に取得したデータは不完全・古い場合があります。未表示を未受注と断定しないでください。進捗50%/80%は段階表示です。\n\n");
        b.append("【受注中・達成未受領】\n");
        if(active.isEmpty())b.append("（未取得。ゲームの任務一覧を開いてください）\n");
        for(Quest q:active)appendQuest(b,q,includeUnaccepted);
        if(includeUnaccepted){
            b.append("\n【観測済み未受注】\n");
            quests.values().stream().filter(q->q.state==1)
                .sorted(Comparator.comparingInt(Quest::id)).forEach(q->appendQuest(b,q,true));
        }
        return b.toString();
    }
    private static void appendQuest(StringBuilder b,Quest q,boolean detailed){
        b.append("- #").append(q.id).append(' ').append(q.title).append(" | ");
        b.append(q.state==3?"達成（未受領）":q.state==2?"受注中":"未受注");
        if(q.state!=3&&q.state!=1)b.append(q.progress==2?" 80%以上":q.progress==1?" 50%以上":" 進捗表示なし");
        if(detailed&&!q.detail.isBlank())b.append(" | ").append(q.detail);
        b.append(" | 確認").append(q.seen).append('\n');
    }
    private static int parseInt(String str,int fallback){
        try{int x=Integer.parseInt(str);return x<0?fallback:x;}catch(RuntimeException e){return fallback;}
    }
    private static int number(Object value,int fallback){
        Integer n=optionalNumber(value);return n==null?fallback:n;
    }
    private static Integer optionalNumber(Object value){
        try{return value==null?null:Integer.valueOf(value.toString());}
        catch(RuntimeException e){return null;}
    }
    private static String strip(Object value,int max){
        if(!(value instanceof String))return "";
        String text=((String)value).replaceAll("(?i)<br\\s*/?>"," ").replaceAll("<[^>]*>","")
            .replaceAll("[\\p{Cntrl}]"," ").replaceAll("\\s+"," ").trim();
        return text.substring(0,Math.min(max,text.length()));
    }

    /** Self-contained limited JSON reader: no network or external dependency. */
    static final class Json {
        private final String s; private int p;
        private Json(String s){this.s=s;}
        static Object parse(String s){
            Json j=new Json(s);Object v=j.value(0);j.space();
            if(j.p!=s.length())throw new IllegalArgumentException("json trailing content");
            return v;
        }
        private void space(){while(p<s.length()&&Character.isWhitespace(s.charAt(p)))p++;}
        private char peek(){if(p>=s.length())throw new IllegalArgumentException("json eof");return s.charAt(p);}
        private void want(char c){space();if(peek()!=c)throw new IllegalArgumentException("json expected "+c);p++;}
        private Object value(int depth){
            if(depth>64)throw new IllegalArgumentException("json nesting");
            space();char c=peek();
            if(c=='{'){
                p++;Map<String,Object> m=new LinkedHashMap<>();space();
                if(peek()=='}'){p++;return m;}
                do{
                    space();String k=string();want(':');m.put(k,value(depth+1));space();
                    char end=peek();p++;if(end=='}')return m;
                    if(end!=',')throw new IllegalArgumentException("json object comma");
                }while(true);
            }
            if(c=='['){
                p++;List<Object> a=new ArrayList<>();space();
                if(peek()==']'){p++;return a;}
                do{
                    a.add(value(depth+1));space();char end=peek();p++;
                    if(end==']')return a;
                    if(end!=',')throw new IllegalArgumentException("json array comma");
                }while(true);
            }
            if(c=='"')return string();
            if(s.startsWith("true",p)){p+=4;return true;}
            if(s.startsWith("false",p)){p+=5;return false;}
            if(s.startsWith("null",p)){p+=4;return null;}
            int start=p;
            if(c=='-')p++;
            while(p<s.length()&&("0123456789.eE+-".indexOf(s.charAt(p))>=0))p++;
            if(p==start)throw new IllegalArgumentException("json value");
            String token=s.substring(start,p);
            if(!token.matches("-?(0|[1-9]\\d*)(\\.\\d+)?([eE][+-]?\\d+)?"))throw new IllegalArgumentException("json number");
            return new java.math.BigDecimal(token);
        }
        private String string(){
            want('"');StringBuilder b=new StringBuilder();
            while(true){
                char c=peek();p++;
                if(c=='"')return b.toString();
                if(c<32)throw new IllegalArgumentException("json control");
                if(c!='\\'){b.append(c);continue;}
                char e=peek();p++;
                switch(e){
                    case '"','\\','/'->b.append(e);
                    case 'b'->b.append('\b');
                    case 'f'->b.append('\f');
                    case 'n'->b.append('\n');
                    case 'r'->b.append('\r');
                    case 't'->b.append('\t');
                    case 'u'->{
                        if(p+4>s.length())throw new IllegalArgumentException("json unicode");
                        b.append((char)Integer.parseInt(s.substring(p,p+4),16));p+=4;
                    }
                    default->throw new IllegalArgumentException("json escape");
                }
            }
        }
    }
}
