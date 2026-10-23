package local.kancolle.kqs;

import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import logbook.bean.AppQuest;
import logbook.bean.QuestList;

/** Verifies full quest list, completeness disclaimers, metadata, and privacy. */
public final class QuestExporterTest {
    private static AppQuest makeQuest(int id, String title, int state, int progress) {
        QuestList.Quest q = new QuestList.Quest();
        q.setNo(id);
        q.setTitle(title);
        q.setDetail("指定海域でS勝利する");
        q.setCategory(2);
        q.setType(4);
        q.setState(state);
        q.setProgressFlag(progress);
        q.setGetMaterial(List.of(0, 100, 200, 1));
        q.setBonusFlag(1);
        q.setInvalidFlag(0);
        AppQuest a = new AppQuest();
        a.setNo(id);
        a.setQuest(q);
        a.setActive(state==2 || state==3);
        a.setExpire(null);
        return a;
    }
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        QuestStore store = new QuestStore();
        List<AppQuest> all = List.of(
            makeQuest(11,"未受注任務",1,0),
            makeQuest(12,"受注中任務",2,1),
            makeQuest(13,"達成・未受領任務",3,2));

        Map<String,Object> before = QuestExporter.build(store, all, false);
        JsonNode initial = new ObjectMapper().readTree(QuestExporter.encode(before));
        check(initial.path("quests").size()==3, "not only accepted tasks");
        check(initial.path("counts").path("available_unaccepted").asInt()==1,"unaccepted count");
        check(initial.path("counts").path("accepted").asInt()==1,"accepted count");
        check(initial.path("counts").path("completed_unclaimed").asInt()==1,"completed count");
        check(!initial.path("all_tab_current_session").asBoolean(),"missing all-tab evidence");
        check(initial.path("quests").get(0).path("basic_reward_materials").size()==4,"reward materials");
        check(initial.path("quests").get(1).path("progress_stage").asText().contains("50"),"progress stage");
        check(initial.path("quests").get(0).path("prerequisite_quest_ids").isNull(),"unknown prerequisites explicit");
        check(initial.path("quests").get(0).path("choice_reward_details").isNull(),"unknown choice reward explicit");

        String example="svdata={\"api_result\":1,\"api_data\":{\"api_count\":3,\"api_exec_count\":2,\"api_page_count\":1,\"api_list\":[{\"api_no\":11,\"api_title\":\"未受注任務\",\"api_state\":1}]}}";
        check(store.accept("/kcsapi/api_get_member/questlist",Map.of("api_tab_id","0","api_page_no","1"),example),"all-tab capture");
        JsonNode observed = new ObjectMapper().readTree(QuestExporter.encode(QuestExporter.build(store,all,false)));
        check(observed.path("all_tab_current_session").asBoolean(),"all-tab observed");
        check(observed.path("capture").path("api_count").asInt()==3,"api total count");
        check(observed.path("capture").path("api_exec_count").asInt()==2,"accepted total count");
        check(!observed.toString().contains("api_token"),"no POST auth parameter");
        check(!observed.toString().contains("SECRET"),"no secret value");
        check(observed.path("scope").asText().contains("logbook_kai"),"source scope");
        System.out.println("QuestExporter tests PASS");
    }
}
