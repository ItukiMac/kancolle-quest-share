package local.kancolle.kqs;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import logbook.bean.AppQuest;
import logbook.bean.AppQuestCollection;
import logbook.bean.AppQuestCondition;
import logbook.bean.QuestList;
import logbook.core.LogBookCoreServices;

/**
 * Explicitly selected fields only. No raw HTTP bodies, cookies, tokens or
 * user account / fleet identifiers are serialized.
 *
 * "All quests" means all quests currently held by logbook-kai's collection,
 * which consists of currently available game quests, not locked quest chains
 * nor historical reward-claimed quests.
 */
public final class QuestExporter {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private QuestExporter() {}

    public static String json(QuestStore store) {
        return encode(build(store, List.copyOf(AppQuestCollection.get().getQuest().values()), true));
    }

    public static String prompt(QuestStore store) {
        return """
            艦これの任務状況を共有します。以下のJSON全件を使って、受注状況と未受注任務も含む任務消化計画を作成してください。

            条件:
            - 期間限定・週次更新を優先し、恒常入手困難な装備・報酬を重視する。
            - 無期限の単発は急がず、資源・戦果・通常開発可能品だけの報酬は低優先とする。
            - 後続任務の解放を含めて調べ、解放に必要な先行任務は優先度を繰り上げる。
            - 出撃先の海域ごとに編成・勝利条件が共通する並走可能任務を整理する。
            - 実際の受注枠数を考慮し、同時受注可能な任務を優先して提案する。
            - 出撃前に並走できる任務を先に提示し、各出撃と対象任務を対応付ける。
            - 不足している艦娘・装備や未確定条件は明示する。任務をゲーム上で勝手に受注しない。
            - 後続・期限・選択報酬などゲームAPIにない情報は wikiwiki.jp/kancolle/ と zekamashi.net で確認し、推測で埋めない。
            - JSON内の観測対象は現在表示可能な任務に限られ、未解放の任務や報酬受領済み任務を含まないことに注意する。

            JSON:
            """ + json(store);
    }

    /** Also used by isolated tests so we can prove accepted AND unaccepted are exported. */
    static Map<String, Object> build(QuestStore store, Collection<AppQuest> values, boolean enrich) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schema", "kqs.quest-snapshot.v2");
        root.put("product", "KQS Logbook Plugin");
        root.put("generated_at_jst", ZonedDateTime.now(ZoneId.of("Asia/Tokyo")).toString());
        root.put("scope", "all_quests_currently_known_to_logbook_kai");
        root.put("meaning_of_all", "available/unlocked quests observed by game and cached in logbook-kai; NOT locked prerequisites, historical completions, or all quests in the game");
        Map<String, Object> capture = store.captureEvidence();
        root.put("capture", capture);
        Map<String,Object> completeness=store.validate(values);
        root.put("completeness",completeness);
        root.put("snapshot_verified",completeness.get("verified"));
        root.put("all_tab_current_session", Boolean.TRUE.equals(capture.get("all_tab_observed_this_session")));
        List<String> limitations = new ArrayList<>();
        limitations.add("The game's currently locked / unreleased / prerequisite-dependent quests are not enumerated by questlist.");
        limitations.add("Claimed-complete quests may disappear. Their historical completion is unknown without other evidence.");
        limitations.add("Some cached quests may be stale until the game quest screen ALL tab has been opened in this session.");
        limitations.add("50% and 80% are UI progress stages, not exact mission counts or per-sea-area progress.");
        limitations.add("Prerequisites, unlock chains, real event end dates and full selectable equipment rewards require external wiki confirmation.");
        if (!Boolean.TRUE.equals(capture.get("all_tab_observed_this_session"))) {
            limitations.add("KQS has NOT witnessed the game's 全て / ALL quest tab this session: current completeness cannot be verified.");
        }
        root.put("limitations", limitations);
        if (!Boolean.TRUE.equals(completeness.get("verified"))) {
            limitations.add("Snapshot is NOT verified against a fresh complete ALL-tab API response; use the completeness.status field.");
        }
        root.put("external_reference_sites", List.of("https://wikiwiki.jp/kancolle/", "https://zekamashi.net"));

        List<Map<String, Object>> quests = new ArrayList<>();
        int accepted = 0, completedPending = 0, unaccepted = 0, other = 0;
        List<AppQuest> sorted = values.stream().filter(Objects::nonNull).filter(q -> q.getQuest()!=null)
            .sorted(Comparator.comparing(q -> q.getNo()==null?Integer.MAX_VALUE:q.getNo())).toList();
        for (AppQuest app : sorted) {
            QuestList.Quest q = app.getQuest();
            int state = q.getState()==null ? -1 : q.getState();
            String status = switch (state) {
                case 1 -> "available_unaccepted";
                case 2 -> "accepted";
                case 3 -> "completed_unclaimed";
                default -> "unknown";
            };
            switch (state) {
                case 1 -> unaccepted++;
                case 2 -> accepted++;
                case 3 -> completedPending++;
                default -> other++;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            Integer id = q.getNo();
            item.put("id", id);
            item.put("title", clean(q.getTitle()));
            item.put("detail", clean(q.getDetail()));
            item.put("category_code", q.getCategory());
            item.put("category", category(q.getCategory()));
            item.put("type_code", q.getType());
            item.put("recurrence_api", recurrence(q.getType()));
            item.put("state_code", q.getState());
            item.put("state", status);
            item.put("is_accepted_or_completed", state==2 || state==3);
            item.put("progress_stage_code", q.getProgressFlag());
            item.put("progress_stage", progress(q.getProgressFlag(),state));
            item.put("basic_reward_materials", q.getGetMaterial()==null?null:List.copyOf(q.getGetMaterial()));
            item.put("has_bonus_reward_flag", q.getBonusFlag());
            item.put("invalid_flag", q.getInvalidFlag());
            item.put("logbook_reset_estimate_at", app.getExpire());
            item.put("locally_defined_custom_quest", id!=null&&id>=10001&&id<=10005);
            item.put("game_event_end_at", null);
            item.put("prerequisite_quest_ids", null);
            item.put("unlocks_quest_ids", null);
            item.put("choice_reward_details", null);
            item.put("exact_objective_counter", null);
            Map<String,Object> local=null;
            if (enrich && id!=null) {
                try { local=condition(id); }
                catch (RuntimeException e) { local=null; }
            }
            item.put("local_quest_condition", local);
            item.put("local_condition_role",local==null?"unavailable":"advisory_not_authoritative");
            List<String> warnings=conditionWarnings(q,local);
            item.put("condition_conflict_codes",warnings);
            item.put("condition_needs_external_verification",local==null || !warnings.isEmpty());
            quests.add(item);
        }
        root.put("counts", Map.of("total_known", quests.size(), "accepted", accepted,
            "completed_unclaimed", completedPending, "available_unaccepted", unaccepted, "unknown", other));
        root.put("quests", quests);
        return root;
    }

    /**
     * API description is authoritative for the current quest text; packaged
     * logbook conditions can lag behind reused event quest IDs.
     */
    private static List<String> conditionWarnings(QuestList.Quest api,Map<String,Object> local) {
        List<String> warnings=new ArrayList<>();
        if(local==null)return warnings;
        Object reset=local.get("reset_type");
        String period=api.getType()==null?"unknown":recurrence(api.getType());
        String localPeriod=canonicalReset(reset);
        if(!"unknown".equals(localPeriod) && !period.equals("other_or_quarterly_or_yearly")
                && !period.equals("unknown") && !localPeriod.equals(period)) {
            warnings.add("api_local_recurrence_conflict");
        }
        // Flag an ambiguous name-list discrepancy rather than asserting the
        // local fleet rule is wrong: remodel names or shorthand can differ.
        Object filter=local.get("filter");
        if(filter instanceof Map<?,?> fm && fm.get("fleet") instanceof Map<?,?> fleet) {
            String detail=clean(api.getDetail());
            if(fleet.get("ship_names") instanceof Collection<?> names) {
                int unmentioned=0;
                for(Object name:names)if(name instanceof String str && !detail.contains(str))unmentioned++;
                if(!names.isEmpty() && unmentioned>0)
                    warnings.add("possible_fleet_name_conflict_check_api_detail");
            }
        }
        return warnings;
    }

    private static String canonicalReset(Object raw) {
        if(!(raw instanceof String s))return "unknown";
        return switch(s) {
            case "デイリー" -> "daily";
            case "ウィークリー" -> "weekly";
            case "マンスリー" -> "monthly";
            case "単発" -> "one_time";
            case "クオータリー","クォータリー" -> "quarterly";
            case "イヤリー" -> "yearly";
            default -> "unknown";
        };
    }

    private static Map<String, Object> condition(int id) {
        AppQuestCondition c = AppQuestCondition.loadFromResource(id);
        if (c==null) return null;
        Map<String, Object> data = new LinkedHashMap<>();
        String origin = LogBookCoreServices.getQuestResource(id,true)!=null ? "logbook_regular"
            : LogBookCoreServices.getCustomQuestResource(id)!=null ? "logbook_custom"
            : LogBookCoreServices.getQuestResource(id,false)!=null ? "logbook_event" : "unknown";
        data.put("source", origin);
        data.put("reset_type", c.getResetType());
        data.put("yearly_reset_month", c.getYearlyResetMonth());
        data.put("tracking_type", c.getType()==null?null:c.getType().toString());
        data.put("start_date", c.getStartDate());
        data.put("custom_name", c.getCustomName());
        if (c.getFilter()!=null) {
            Map<String,Object> filter = new LinkedHashMap<>();
            filter.put("areas", c.getFilter().getArea());
            filter.put("fleet", fleet(c.getFilter().getFleet(),0));
            data.put("filter", filter);
        } else data.put("filter",null);
        List<Map<String,Object>> objectives = new ArrayList<>();
        if (c.getConditions()!=null) {
            for (AppQuestCondition.Condition v:c.getConditions()) {
                Map<String,Object> o = new LinkedHashMap<>();
                o.put("areas", v.getArea());
                o.put("node", v.getCell());
                o.put("nodes", v.getCells());
                o.put("sortie_starts", v.isStart());
                o.put("boss", v.isBoss());
                o.put("victory_ranks", v.getRank());
                o.put("target_ship_types", v.getStype());
                o.put("required_count", v.getCount());
                o.put("expeditions", v.getMissions());
                o.put("description", v.getDescription());
                o.put("estimated_current_count", v.getCurrent());
                objectives.add(o);
            }
        }
        data.put("objectives", objectives);
        return data;
    }

    private static Map<String,Object> fleet(AppQuestCondition.FleetCondition f,int depth) {
        if (f==null || depth>12) return null;
        Map<String,Object> m=new LinkedHashMap<>();
        m.put("ship_names", f.getName());
        m.put("ship_types", f.getStype());
        m.put("exclude", f.isDifference());
        m.put("operator", f.getOperator());
        m.put("count", f.getCount());
        m.put("order", f.getOrder());
        m.put("description", f.getDescription());
        if(f.getConditions()!=null) {
            List<Map<String,Object>> children=new ArrayList<>();
            for(AppQuestCondition.FleetCondition c:f.getConditions())children.add(fleet(c,depth+1));
            m.put("conditions",children);
        }
        return m;
    }

    static String encode(Map<String,Object> data) {
        try { return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(data); }
        catch (JsonProcessingException e) { throw new IllegalStateException("JSON export failed", e); }
    }
    private static String clean(String s) {
        return s==null?"":s.replaceAll("(?i)<br\\s*/?>"," ").replaceAll("<[^>]*>","")
            .replaceAll("[\\p{Cntrl}]"," ").replaceAll("\\s+"," ").trim();
    }
    private static String progress(Integer v,int state) {
        if(state==3)return "completed";
        if(v==null)return "unknown";
        return switch(v){case 1->"at_least_50_percent_stage";case 2->"at_least_80_percent_stage";
            default->"no_progress_stage";};
    }
    private static String recurrence(Integer n) {
        if(n==null)return "unknown";
        return switch(n){case 1->"daily";case 2->"weekly";case 3->"monthly";
            case 4->"one_time";case 5->"other_or_quarterly_or_yearly";default->"unknown";};
    }
    private static String category(Integer n) {
        if(n==null)return "unknown";
        return switch(n){case 1->"formation";case 2,8,9,10->"sortie";case 3->"practice";
            case 4->"expedition";case 5->"supply";case 6->"factory";
            case 7->"modernization";case 11->"factory";default->"other";};
    }
}
