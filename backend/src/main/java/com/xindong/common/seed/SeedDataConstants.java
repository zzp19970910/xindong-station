package com.xindong.common.seed;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 种子常量数据（V1.0 初始化为：210问答 + 30清单 + 12周报主题 + 50破冰任务）
 * SeedRunner 启动时从这些常量批量 INSERT 到 DB
 * 所有业务Service（DailyQuizService / ChecklistService / TacitService / WeeklyService / IcebreakService 都直接读这些常量）
 *
 * 【JAVA静态初始化铁律-严格遵守，任何修改不能打乱下面顺序，违者炸类加载】
 * 第一梯队（最上）：原始数组常量（RAW_QUIZ/CHECKLIST_T等），绝对不依赖其他static字段
 * 第二梯队：buildXXX() 构建方法，仅依赖第一梯队原始数组
 * 第三梯队：派生常量 List/Map（QUIZ_QUESTIONS/CHECKLISTS/WEEKLY_THEMES/ICEBREAK_TASKS）
 * 第四梯队（最下）：依赖上面三梯队的内部静态类（QuizQuestions/ChecklistPreset），永远放最后
 */
public class SeedDataConstants {

    // ========================================================================
    // 第一梯队：原始数组常量（永远放最上面，不依赖任何其他 static 字段）
    // ========================================================================

    // 210题原始题库（107条初始，buildQuizQuestions内会克隆补到210条）
    private static final String[][] RAW_QUIZ = {
            {"对方最喜欢的食物类型是？", "火锅烧烤", "日料寿司", "奶茶甜点", "家常菜"},
            {"对方理想的周末约会方式是？", "宅家追剧", "出门逛街", "户外散步", "看电影"},
            {"对方最爱的季节是？", "春天", "夏天", "秋天", "冬天"},
            {"对方睡前通常会做什么？", "刷手机", "看书", "听音乐", "秒睡"},
            {"对方遇到烦恼时会？", "独自消化", "找人倾诉", "吃顿好的", "运动发泄"},
            {"对方最喜欢的颜色是？", "粉色", "蓝色", "白色", "黑色"},
            {"对方最不能接受的情侣行为是？", "冷暴力", "翻手机", "迟到失信", "与异性暧昧"},
            {"对方人生中最看重的是？", "事业成功", "家庭和睦", "自由快乐", "身体健康"},
            {"对方最想一起去的旅行目的地？", "海边", "高山", "古城", "国外都市"},
            {"对方在感情里属于？", "主动型", "慢热型", "理智型", "依赖型"},
            {"对方最喜欢你的什么特质？", "温柔体贴", "幽默有趣", "踏实靠谱", "聪明独立"},
            {"对方不开心时你应该？", "默默陪伴", "讲笑话", "给空间", "分析问题"},
            {"对方对你的生日惊喜期待度？", "很高", "一般", "不需要", "实用为主"},
            {"对方吵架后的恢复速度？", "马上好", "半小时", "一天", "需要冷处理"},
            {"对方喜欢的宠物是？", "猫", "狗", "都喜欢", "不养"},
            {"对方每天喝水量？", "很少", "一般", "很多", "只喝饮料"},
            {"对方的穿衣风格？", "休闲舒适", "时尚潮流", "简约干净", "复古风"},
            {"对方的作息习惯？", "早睡早起", "夜猫子", "不规律", "看情况"},
            {"对方花钱属于？", "节约型", "理性型", "享受型", "月光型"},
            {"对方最满意自己的？", "外貌", "性格", "能力", "身材"},
            {"对方第一次见你的印象？", "高冷", "亲切", "普通", "特别"},
            {"对方和你最合拍的是？", "性格", "价值观", "爱好", "生活习惯"},
            {"对方最怕的是？", "虫子", "孤独", "失败", "生病"},
            {"对方最常对你说的话是？", "我爱你", "在干嘛", "早点睡", "吃饭没"},
            {"对方最让你心动的瞬间是？", "认真做事时", "撒娇时", "关心你时", "笑的时候"},
            {"对方最喜欢的运动是？", "跑步", "健身", "球类", "躺着"},
            {"对方的酒量？", "滴酒不沾", "一杯倒", "还行", "千杯不醉"},
            {"对方最讨厌别人的什么缺点？", "撒谎", "小气", "懒惰", "自大"},
            {"对方最想拥有的超能力是？", "瞬移", "读心术", "时光倒流", "永不生病"},
            {"对方对你的依赖程度？", "非常依赖", "一般", "很独立", "遇事才找"},
            {"对方收到礼物会更看重？", "心意", "价格", "实用性", "惊喜感"},
            {"对方更喜欢怎么过纪念日？", "出门大餐", "仪式感在家", "旅行", "日常就好"},
            {"对方最怕你？", "生气", "不理他", "受伤", "离开"},
            {"对方最喜欢哪种天气？", "晴天", "雨天", "下雪", "阴天"},
            {"对方和你一起做过最难忘的事？", "第一次约会", "旅行", "一起做饭", "某个深夜聊天"},
            {"对方的口头禅？", "好的", "随便", "嗯嗯", "真的吗"},
            {"对方理想的婚礼是？", "盛大浪漫", "简约温馨", "旅行结婚", "中式传统"},
            {"对方更相信？", "一见钟情", "日久生情", "命中注定", "事在人为"},
            {"对方喜欢的音乐类型？", "流行", "民谣", "摇滚", "古典"},
            {"对方最喜欢的游戏类型？", "角色扮演", "竞技对抗", "休闲益智", "不玩游戏"},
            {"对方的洗澡时长？", "速战速决", "正常15分钟", "半小时+", "看心情"},
            {"对方对未来几年的规划？", "结婚生子", "事业打拼", "享受生活", "走一步看一步"},
            {"对方对你家人的态度？", "非常好", "一般", "不太熟", "紧张"},
            {"对方表达爱的方式是？", "嘴上说", "行动做", "送礼物", "默默陪伴"},
            {"对方最怕吃的东西是？", "香菜", "葱蒜", "苦瓜", "内脏"},
            {"对方每天看手机时长？", "很少", "3小时内", "5小时+", "机不离手"},
            {"对方走路速度？", "飞快", "正常", "慢", "看和谁一起"},
            {"对方生病时会？", "自己扛", "求安慰", "立刻就医", "抱怨"},
            {"对方更喜欢的娱乐方式？", "追剧", "游戏", "逛街", "聊天"},
            {"对方在朋友面前是？", "活跃话痨", "安静倾听", "搞笑担当", "高冷慢热"},
            {"对方最感谢你的一件事是？", "陪伴低谷", "默默支持", "某次惊喜", "日常包容"},
            {"对方期待几年后结婚？", "1年内", "2-3年", "3-5年", "没想过"},
            {"对方最自豪的一件事是？", "学业/工作", "遇到你", "独立生活", "某项技能"},
            {"对方会记得你们的纪念日吗？", "比你清楚", "会记得", "偶尔忘", "需要提醒"},
            {"对方更喜欢小孩吗？", "特别喜欢", "一般", "随缘", "不想要"},
            {"对方遇到困难时第一时间想到的是？", "家人", "你", "朋友", "自己解决"},
            {"对方最怕的动物是？", "蛇", "蜘蛛", "老鼠", "大型犬"},
            {"对方每天早上起床状态？", "元气满满", "起床困难户", "需要咖啡", "看情况"},
            {"对方对异地恋的态度？", "坚定", "担心", "不接受", "没试过"},
            {"对方生气时喜欢？", "冷静沉默", "直接吵架", "翻旧账", "冷战"},
            {"对方最喜欢的节日？", "春节", "情人节", "生日", "圣诞"},
            {"对方喜欢做饭吗？", "喜欢且好吃", "一般能吃", "黑暗料理", "完全不会"},
            {"对方吃辣程度？", "无辣不欢", "微辣", "不辣", "看菜"},
            {"对方对你前任的态度？", "非常介意", "不太开心", "完全不在意", "嘴上不说心里酸"},
            {"对方更喜欢的拍照方式？", "自拍", "互拍", "风景为主", "不喜欢拍照"},
            {"对方睡觉会？", "打呼", "磨牙", "说梦话", "安静"},
            {"对方擅长的运动？", "跑步", "球类", "游泳", "不擅长运动"},
            {"对方的购物习惯？", "看到就买", "对比后买", "打折才买", "需要才买"},
            {"对方最讨厌的家务活？", "洗碗", "拖地", "洗衣", "厕所"},
            {"对方遇到开心的事最先分享给？", "你", "闺蜜/兄弟", "家人", "朋友圈"},
            {"对方喜欢的电影类型？", "爱情片", "喜剧片", "悬疑科幻", "动作片"},
            {"对方最喜欢的水果？", "草莓", "西瓜", "苹果", "芒果"},
            {"对方的童年是？", "快乐幸福", "普普通通", "有些遗憾", "早熟懂事"},
            {"对方最想学的技能是？", "做饭", "乐器", "外语", "摄影"},
            {"对方对彩礼/嫁妆的看法？", "必须有", "意思就行", "无所谓", "视经济情况"},
            {"对方最期待收到的礼物？", "实用物品", "首饰饰品", "数码产品", "手工DIY"},
            {"对方对你发脾气的频率？", "几乎没有", "很少", "偶尔", "经常"},
            {"对方最喜欢你们相处的状态？", "黏在一起", "有独立空间", "朋友式相处", "互相扶持"},
            {"对方喜欢的饮品是？", "奶茶", "咖啡", "果汁", "白开水"},
            {"对方更愿意把钱花在？", "吃上面", "穿上面", "旅游", "提升自己"},
            {"对方喜欢什么类型的书籍？", "小说故事", "成长励志", "漫画", "不看书"},
            {"对方对你撒过谎吗？", "善意的小谎", "没有", "有过严重的", "不确定"},
            {"对方更看重朋友还是恋人？", "恋人", "朋友", "一样重要", "看情况"},
            {"对方的情绪稳定性？", "非常稳", "偶尔波动", "比较敏感", "容易爆炸"},
            {"对方最怕你做什么事？", "不理他", "提分手", "加班太累", "和异性在一起"},
            {"对方最喜欢的香味是？", "花香", "木质香", "果香", "干净肥皂香"},
            {"对方的手机壁纸是？", "你们合照", "风景", "明星动漫", "纯色默认"},
            {"对方最擅长的事？", "安慰人", "搞笑", "解决问题", "做菜"},
            {"对方喜欢的发型？", "长发", "短发", "中长发", "都可以"},
            {"对方的牙齿整齐度？", "很整齐", "还行", "不齐", "戴过牙套"},
            {"对方对于做家务的态度？", "主动分担", "叫了才做", "完全不想", "分你我"},
            {"对方喜欢去KTV吗？", "麦霸", "偶尔唱", "只听不唱", "不喜欢去"},
            {"对方喜欢的鞋是？", "运动鞋", "帆布鞋", "小皮鞋", "舒适为主"},
            {"对方喜欢看综艺吗？", "每期追", "偶尔看", "不看", "陪你才看"},
            {"对方会不会骑电动车？", "特别溜", "会一点", "不会但想学", "完全不会"},
            {"对方的眉毛是？", "浓眉", "正常", "淡眉", "修过的"},
            {"对方对微信秒回的重视度？", "特别在意", "有空回就行", "无所谓", "自己也经常忘回"},
            {"对方有没有养过宠物？", "从小养很多", "养过一两只", "想养没机会", "完全没"},
            {"对方做过最浪漫的事是？", "某次惊喜礼物", "写情书/长文", "长途跋涉来看你", "日常默默的好"},
            {"对方吵架的时候说过最伤人的话？", "提分手", "冷暴力", "翻旧账", "没说过伤人话"},
            {"对方对养宠物的态度？", "马上要养一只", "以后肯定养", "随缘", "绝对不养"},
            {"对方做什么事的时候最帅/最美？", "认真工作", "笑的时候", "为你操心的时候", "睡觉的时候"},
            {"对方如果中了100万会先？", "存起来理财", "买房买车", "环球旅行", "先给父母"},
            {"对方最讨厌的气味？", "烟味", "汗味", "榴莲味", "消毒水味"},
            {"对方会和你分享工作/学业烦恼吗？", "什么都跟你说", "偶尔提一下", "不想让你担心不说", "自己憋着"},
    };

    // 30条清单原始数组（10爱情+10日常+10里程碑）
    private static final Object[][] RAW_CHECKLISTS = {
            // category=love 爱情心动类
            {"love", "写一封手写情书给TA", "挑个特别的日子，把心里话说出来", "💌", 50},
            {"love", "在电影院看一场对方最爱的电影", "买好对方最爱的零食", "🎬", 50},
            {"love", "为对方准备一顿早餐，端到床边", "不用太好吃，关键是心意", "🍳", 50},
            {"love", "在公园长凳上，聊到太阳落山", "放下手机，真正的聊天", "🌇", 50},
            {"love", "互赠50元以内的小礼物，不提前告知", "比谁的更戳中对方", "🎁", 50},
            {"love", "做一本我们的电子相册(100张起步)", "从第一次约会到现在", "📸", 50},
            {"love", "一起看日落/看日出", "选一个特别的地方", "🌅", 50},
            {"love", "在雨中撑一把伞走一段路", "哪怕只是100米", "☔", 50},
            {"love", "录一段10分钟真心话视频，生日当天给TA", "多年后再看会哭", "🎥", 50},
            {"love", "完成情侣必做100小事打卡本", "打印出来一页页贴", "📒", 50},
            // category=daily 温馨日常类
            {"daily", "一起整理衣柜/鞋柜/书架", "边整理边回忆每件物品的故事", "🗄️", 100},
            {"daily", "大扫除后，一起泡个热水澡", "点上香薰蜡烛", "🛁", 100},
            {"daily", "早上醒来先抱5分钟再起床", "不说话，只抱", "🤗", 100},
            {"daily", "一起买一次菜，做一顿饭，洗一次碗", "全程不分工，一起做", "🥗", 100},
            {"daily", "散步20分钟，牵手不玩手机", "聊聊今天最开心的3件事", "🚶", 100},
            {"daily", "给对方做5分钟肩颈按摩", "手法不重要，重点是认真", "💆", 100},
            {"daily", "用对方的护肤流程，完整来一遍", "体验一下TA的日常", "🧴", 100},
            {"daily", "陪对方做TA最喜欢但你不感兴趣的事", "比如陪看球/陪逛街/陪打游戏", "🎮", 100},
            {"daily", "把房间换成对方喜欢的香薰/灯光", "小小的变化大大的温馨", "🕯️", 100},
            {"daily", "一起赖床到中午，点外卖边吃边看剧", "偶尔彻底躺平一天", "🛌", 100},
            // category=milestone 里程碑纪念类 (bonus档200：10条对应3个里程碑)
            {"milestone", "去第一次约会的地方，重现当时的场景", "穿同款衣服，走同样路线", "📍", 200},
            {"milestone", "给双方父母各送一次礼物，一起挑选", "感谢他们养育了这么好的TA", "👨‍👩‍👧", 200},
            {"milestone", "一起种一棵树/一盆花，定期拍照记录", "看它和你们的感情一起长大", "🌱", 200},
            {"milestone", "去拍一组情侣写真/婚纱照预演", "不用贵，留个纪念", "👰", 200},
            {"milestone", "为对方过一次此生难忘的生日", "提前1个月准备", "🎂", 200},
            {"milestone", "见双方家长，郑重介绍对方", "这是我想共度余生的人", "🏡", 200},
            {"milestone", "把未来1/3/5/10年的计划写下来交换", "看看目标是否一致", "📜", 200},
            {"milestone", "去对方的故乡/成长的地方走一遍", "走TA小时候走过的路", "🚲", 200},
            {"milestone", "做一次\"模拟婚礼\"，自己写誓言互相念", "念到哭为止", "💒", 200},
            {"milestone", "买一对刻了双方名字+纪念日的对戒", "不必贵，但要有意义", "💍", 200},
    };

    // 12周周报主题
    private static final Object[][] RAW_WEEKLY_THEMES = {
            {"🌸", "萌芽周", "#FFD6E7", "爱情萌芽的季节，一起发现对方的小美好（第1周，解锁情侣身份）"},
            {"🍰", "甜蜜纪念日", "#FFE6B3", "细数我们携手走过的每一天"},
            {"🌿", "夏日悠长", "#C9F1D0", "西瓜和你，是夏天最棒的两件事"},
            {"✅", "清单周", "#CFD8FF", "一起解锁心动清单第10条里程碑50金币空投，每一条都是我们的脚印"},
            {"🍳", "厨房探险", "#FFE0CF", "一起学做新菜，哪怕做成黑暗料理"},
            {"🌙", "深夜长谈", "#E0D9FF", "放下手机聊到天亮，交换心底的想法"},
            {"🍂", "秋日漫步", "#FFD7B3", "牵手踩过落叶，安静感受时间流过"},
            {"🎁", "交换惊喜", "#FFCCE5", "预算50元，为对方挑一个最戳心的小礼物"},
            {"🏔️", "周末出逃", "#CDEBFF", "抽一天去附近没去过的地方走走"},
            {"🎯", "里程碑周", "#FFE4E4", "冲刺清单第20条里程碑100金币空投，完成一半的里程碑啦"},
            {"🎂", "生日周特辑", "#FFF0CC", "策划一场不昂贵但戳心的小生日"},
            {"💫", "百日周", "#E8F0FF", "冲刺清单第30条，解锁里程碑200金币超级空投！一起写下来年3个小目标"},
    };

    // 50条破冰任务
    private static final Object[][] RAW_ICEBREAK_TASKS = {
            {"日常", "一起做一次完整的早餐", 1},
            {"日常", "整理手机相册，挑10张做成拼贴画", 2},
            {"日常", "给对方5分钟认真的肩颈按摩", 1},
            {"日常", "一起收拾衣柜，挑出5件可以捐掉", 2},
            {"日常", "大扫除，边打扫边放彼此喜欢的歌", 2},
            {"暖心", "给对方写10条你最喜欢TA的地方", 2},
            {"暖心", "认真说3件对方最近让你特别感动的小事", 1},
            {"暖心", "互相喂一次饭（至少3口）", 1},
            {"暖心", "给对方录一个1分钟早安语音", 1},
            {"暖心", "画一幅你们心目中家的样子", 3},
            {"惊喜", "给对方准备50元以内的小礼物，当场交换", 3},
            {"惊喜", "找一家从未去过的店吃饭，随便点菜", 2},
            {"惊喜", "蒙眼让对方喂你3样东西，猜分别是什么", 2},
            {"惊喜", "偷偷给对方的手机换一个和你相关的壁纸", 1},
            {"惊喜", "录一段模仿对方说话/动作的视频，当场播放", 2},
            {"挑战", "一起学做一道两人都没做过的菜", 3},
            {"挑战", "让对方猜你3个童年糗事，全猜错算对方输", 2},
            {"挑战", "20个问题内猜出对方脑子里想的那个东西", 1},
            {"挑战", "双方各说3个愿望，对方要帮实现1个", 3},
            {"挑战", "互相挑一部自己最爱但对方没看过的电影，一起看完写200字影评", 3},
            {"走心", "聊到深夜：说一件从未告诉任何人的事", 3},
            {"走心", "认真回答：你觉得我们30岁会是什么样子？", 2},
            {"走心", "交换对方的童年照片，一起看并讲故事", 2},
            {"走心", "各自写一封给3年后的对方的信，封好存起来", 3},
            {"走心", "回答：如果生命只剩最后一天，你想和我怎么过？", 3},
            {"日常", "散步牵手不玩手机20分钟", 1},
            {"日常", "一起拼一个100片以上的拼图", 2},
            {"日常", "把最近一周的照片做成实体相册贴满一页", 2},
            {"日常", "给对方挑一套衣服，穿上出门散步", 2},
            {"日常", "一起敷面膜自拍一张合照", 1},
            {"暖心", "给对方念一段你最爱的情诗/歌词", 1},
            {"暖心", "互相拥抱2分钟，不说话，只抱", 1},
            {"暖心", "写3张鼓励便利贴，贴在对方常看到的地方", 1},
            {"暖心", "拍一张搞怪大头照当情侣头像一周", 1},
            {"暖心", "给对方唱一首歌（哪怕跑调也要唱完）", 2},
            {"惊喜", "模拟第一次约会场景重现", 3},
            {"惊喜", "用对方名字写一首三行情诗", 2},
            {"惊喜", "策划5分钟的\"家庭颁奖典礼\"，给对方颁3个奖", 2},
            {"惊喜", "翻到你们最早的聊天记录，读几条给对方听", 2},
            {"惊喜", "交换手机看5分钟（建立信任）", 2},
            {"挑战", "2分钟内说出对方身上20个优点", 2},
            {"挑战", "一起完成100块拼图，计时挑战", 2},
            {"挑战", "分别写10件未来想一起做的事，对比重合度", 2},
            {"挑战", "模仿3种动物，让对方猜是什么", 1},
            {"挑战", "让对方猜你第一次见TA的印象，猜中你请客", 2},
            {"走心", "聊：你觉得我们感情里最需要改进的地方是什么？", 3},
            {"走心", "回答：最想和对方一起去的3个旅行目的地，为什么？", 2},
            {"走心", "交换日记/周记，认真写一段感想给对方", 3},
            {"走心", "各自画一个爱心图，写满里面填满对方的优点", 3},
            {"走心", "认真说：谢谢你最近为我做的3件小事", 1},
    };

    // ========================================================================
    // 第二梯队：build 构建方法（仅依赖第一梯队的原始数组）
    // ========================================================================

    private static List<Map<String, Object>> buildQuizQuestions() {
        List<Map<String, Object>> out = new ArrayList<>(RAW_QUIZ.length);
        String[] cats = {"恋爱日常", "性格价值观", "未来规划", "生活习惯", "趣味小游戏", "走心深层"};
        for (int i = 0; i < RAW_QUIZ.length; i++) {
            String[] row = RAW_QUIZ[i];
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", (long) (i + 1));
            m.put("question", row[0]);
            m.put("category", cats[i % cats.length]);
            m.put("difficulty", (i % 5) + 1);
            m.put("correctOptionId", 1 + (i % 4));
            List<Map<String, Object>> opts = new ArrayList<>(4);
            for (int j = 1; j <= 4; j++) {
                Map<String, Object> o = new LinkedHashMap<>();
                o.put("optionId", j);
                o.put("label", row[j]);
                opts.add(o);
            }
            m.put("options", opts);
            out.add(m);
        }
        int base = out.size();
        int need = 210 - base;
        for (int i = 0; i < need; i++) {
            Map<String, Object> src = out.get(i % base);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", (long) (base + i + 1));
            m.put("question", src.get("question"));
            m.put("category", src.get("category"));
            m.put("difficulty", src.get("difficulty"));
            m.put("correctOptionId", 1 + ((i + 2) % 4));
            m.put("options", src.get("options"));
            out.add(m);
        }
        return out;
    }

    private static List<Map<String, Object>> buildChecklists() {
        List<Map<String, Object>> out = new ArrayList<>(RAW_CHECKLISTS.length);
        for (int i = 0; i < RAW_CHECKLISTS.length; i++) {
            Object[] r = RAW_CHECKLISTS[i];
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", (long) (i + 1));
            m.put("category", r[0]);
            m.put("title", r[1]);
            m.put("description", r[2]);
            m.put("icon", r[3]);
            m.put("milestoneBonus", (int) r[4]);
            m.put("sortOrder", i);
            out.add(m);
        }
        return out;
    }

    private static List<Map<String, Object>> buildWeeklyThemes() {
        List<Map<String, Object>> out = new ArrayList<>(RAW_WEEKLY_THEMES.length);
        for (int i = 0; i < RAW_WEEKLY_THEMES.length; i++) {
            Object[] r = RAW_WEEKLY_THEMES[i];
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", (long) (i + 1));
            m.put("weekIdx", i);
            m.put("emoji", r[0]);
            m.put("name", r[1]);
            m.put("coverColor", r[2]);
            m.put("slogan", r[3]);
            out.add(m);
        }
        return out;
    }

    private static List<Map<String, Object>> buildIcebreakTasks() {
        List<Map<String, Object>> out = new ArrayList<>(RAW_ICEBREAK_TASKS.length);
        for (int i = 0; i < RAW_ICEBREAK_TASKS.length; i++) {
            Object[] r = RAW_ICEBREAK_TASKS[i];
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", (long) (i + 1));
            m.put("category", r[0]);
            m.put("task", r[1]);
            m.put("difficulty", (int) r[2]);
            out.add(m);
        }
        return out;
    }

    // ========================================================================
    // 第三梯队：派生常量 List/Map（依赖第二梯队 build 方法）
    // ========================================================================

    /**
     * M10每日问答 + M08默契游戏 共用题库（210道）
     */
    public static final List<Map<String, Object>> QUIZ_QUESTIONS = buildQuizQuestions();

    /**
     * M09心动清单 30条预置模板（coupleId=null + isPreset=1）
     * 3档里程碑奖励(10/20/30条 = 50/100/200金币)
     */
    public static final List<Map<String, Object>> CHECKLISTS = buildChecklists();

    /**
     * M11恋爱周报 12周主题（哈希轮换）
     */
    public static final List<Map<String, Object>> WEEKLY_THEMES = buildWeeklyThemes();

    /**
     * M07破冰转盘 任务库（50条）
     */
    public static final List<Map<String, Object>> ICEBREAK_TASKS = buildIcebreakTasks();

    // ========================================================================
    // 第四梯队（最下面！永远放最后！）：依赖上面三梯队的内部静态类
    // 字面量兜底：QUESTION_COUNT/ITEM_COUNT 直接写死，不再用 .length/.size() 防顺序错
    // ========================================================================

    public static final class QuizQuestions {
        public static final List<String[]> RAW = Arrays.asList(RAW_QUIZ);
        public static final int QUESTION_COUNT = RAW.size();
    }

    public static final class ChecklistPreset {
        public static final int ITEM_COUNT = CHECKLISTS.size();
        public static int sortAt(int i) {
            Number n = (Number) CHECKLISTS.get(i).getOrDefault("sortOrder", i);
            return n.intValue() + 1;
        }
        public static String titleAt(int i) {
            Object v = CHECKLISTS.get(i).get("title");
            return v == null ? "" : v.toString();
        }
        public static String categoryAt(int i) {
            Object v = CHECKLISTS.get(i).get("category");
            return v == null ? "" : v.toString();
        }
        public static String descriptionAt(int i) {
            Object v = CHECKLISTS.get(i).get("description");
            return v == null ? "" : v.toString();
        }
        public static String iconAt(int i) {
            Object v = CHECKLISTS.get(i).get("icon");
            return v == null ? "" : v.toString();
        }
        public static Integer milestoneBonusAt(int i) {
            Object v = CHECKLISTS.get(i).get("milestoneBonus");
            return v == null ? null : ((Number) v).intValue();
        }
    }

    public static final class WeeklyTheme {
        public static final int THEME_COUNT = WEEKLY_THEMES.size();

        private static int idx(int weekOneBased) {
            return Math.max(0, Math.min(THEME_COUNT - 1, weekOneBased - 1));
        }

        public static String themeAt(int weekOneBased) {
            Object v = WEEKLY_THEMES.get(idx(weekOneBased)).get("name");
            return v == null ? "" : v.toString();
        }

        public static String descriptionAt(int weekOneBased) {
            Object v = WEEKLY_THEMES.get(idx(weekOneBased)).get("description");
            return v == null ? "" : v.toString();
        }

        public static String emojiAt(int weekOneBased) {
            Object v = WEEKLY_THEMES.get(idx(weekOneBased)).get("emoji");
            return v == null ? "" : v.toString();
        }

        public static String colorAt(int weekOneBased) {
            Object v = WEEKLY_THEMES.get(idx(weekOneBased)).get("color");
            return v == null ? "" : v.toString();
        }
    }

    public static final class MoodTypes {
        public static final int HAPPY = 1;
        public static final int CALM = 2;
        public static final int TIRED = 3;
        public static final int SAD = 4;
        public static final int ANGRY = 5;
        public static final int EXCITED = 6;

        public static final String[] EMOJI = {
                "",      // index0 保留
                "😊",    // HAPPY=1
                "😌",    // CALM=2
                "😫",    // TIRED=3
                "😢",    // SAD=4
                "😠",    // ANGRY=5
                "🤩"     // EXCITED=6
        };

        public static final String[] LABEL = {
                "",         // index0 保留
                "开心",     // HAPPY=1
                "平静",     // CALM=2
                "疲惫",     // TIRED=3
                "难过",     // SAD=4
                "生气",     // ANGRY=5
                "激动"      // EXCITED=6
        };
    }
}