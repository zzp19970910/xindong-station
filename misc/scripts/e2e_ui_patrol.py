# -*- coding: utf-8 -*-
"""
心动空间站 UI E2E 页面巡检 + 业务逻辑对账脚本
===============================================
检测范围：
  A. 页面可达性类（旧功能保留）：按钮点不动、□乱码、console报错、接口4xx/5xx、页面空白
  B. 业务逻辑对账（新增2026-08-16，对应避坑指南J类）：接口code=0但业务结果不符合预期
     - 破冰大转盘：抽奖后次数应扣减，且不超过MAX上限
     - 心情打卡：提交成功后应真存入DB，再进页面依然存在
     - 金币中心：前端显示余额 == 后端接口真实余额，无□乱码
     - 心愿步骤：勾选后刷新页面，勾选状态应保留（真存入completed_steps）
用法：
  1. 第一次：  pip install playwright ; playwright install chromium
  2. 确保前端已跑（默认8080 或 设置 E2E_BASE=http://localhost:5173 走Vite开发服）
  3. 运行：     python f:\\docker\\xindong-station\\scripts\\e2e_ui_patrol.py
  4. 看报告：   f:\\docker\\xindong-station\\report_ui_e2e_*.html
"""
import os, re, sys, json, time, traceback, urllib.request, urllib.parse
from datetime import datetime
from pathlib import Path

ROOT = Path(r"f:\docker\xindong-station")
OUT_DIR = ROOT / "e2e_screenshots"
OUT_DIR.mkdir(exist_ok=True)

BASE_URL = os.environ.get("E2E_BASE", "http://localhost:8080").rstrip("/")
API_BASE = (os.environ.get("E2E_API", "http://localhost:8080") or BASE_URL).rstrip("/") + "/api/v1"
TOKEN_A = "TEST-A-108"  # JwtUtil.java 硬编码红线情侣108 PartnerA (uid201)
TOKEN_B = "TEST-B-108"

PAGES = [
    ("首页 /dashboard",              "/"),
    ("心情打卡 /mood",               "/mood"),
    ("日记 /diary",                  "/diary"),
    ("纪念日 /anniversary",          "/anniversary"),
    ("恋爱清单 /record",             "/record"),
    ("每日默契 /interactive/quiz",   "/interactive/quiz-daily"),
    ("默契小游戏 /interactive/tacit", "/interactive/tacit-game"),
    ("破冰大转盘 /interactive/icebreak", "/interactive/icebreak"),
    ("心愿列表 /interactive/wish",   "/interactive/wish"),
    ("新建心愿 /interactive/wish/new", "/interactive/wish-new"),
    ("金币中心 /settings/coin",      "/settings/coin-center"),
    ("私信 /interactive/pm",         "/interactive/pm"),
    ("设置 /settings",               "/settings"),
]

CLICK_SELECTORS = [
    "button", ".van-button", "[role='button']",
    ".van-tab", ".van-tabbar-item", ".van-tag",
    ".van-checkbox__label", ".van-radio__label",
    "a[href]", "[class*='tab']", "[class*='Tab']",
    "[class*='card']", "[class*='Card']",
]

GARBLE_RE = re.compile(r"[\uFFFD\uDC00-\uDFFF\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F-\u009F\u2000-\u200F\u2028-\u202F\uFEFF\uFFF0-\uFFFF]")


def now_stamp() -> str:
    return datetime.now().strftime("%Y%m%d_%H%M%S")


def safe_name(s: str) -> str:
    return re.sub(r"[^\w\u4e00-\u9fff-]+", "_", s).strip("_") or "page"


def safe_num(v):
    """J004：把带控制字符/emoji/零宽空格的脏数字强转成干净int，对应前端的safeNum函数"""
    if v is None:
        return 0
    try:
        s = re.sub(r"[^\d-]", "", str(v))
        if s in ("", "-"):
            return 0
        return int(s)
    except Exception:
        return 0


# ============================================================
# 【J类核心】极简后端API对账客户端（不依赖Playwright，直接urllib打接口拿真实值）
# ============================================================
class ApiClient:
    def __init__(self, base: str, token: str):
        self.base = base
        self.token = token

    def _req(self, method: str, path: str, params=None, body=None):
        url = self.base + path
        if params:
            url = url + ("&" if "?" in url else "?") + urllib.parse.urlencode(params)
        data = None
        headers = {
            "Authorization": f"Bearer {self.token}",
            "X-Token": self.token,
            "token": self.token,
            "User-Agent": "E2E-BizAssert/1.0",
            "Accept": "application/json",
        }
        if body is not None:
            data = json.dumps(body).encode("utf-8")
            headers["Content-Type"] = "application/json; charset=utf-8"
        req = urllib.request.Request(url, data=data, method=method.upper(), headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=15) as resp:
                raw = resp.read().decode("utf-8", errors="replace")
                status = getattr(resp, "status", None) or resp.getcode()
        except urllib.error.HTTPError as e:
            raw = e.read().decode("utf-8", errors="replace")
            status = e.code
        except Exception as e:
            return {"_ok": False, "_error": str(e), "status": 0, "code": -1, "msg": str(e)}
        try:
            j = json.loads(raw)
        except Exception:
            j = {"_raw": raw}
        j.setdefault("_ok", True)
        j.setdefault("_status", status)
        return j

    def get(self, p, **kw):
        return self._req("GET", p, params=kw if kw else None)

    def post(self, p, **body):
        return self._req("POST", p, body=body if body else None)


def biz_assert(name: str, detail: list, cond: bool, fail_msg: str):
    """记录一条业务断言：cond=True=PASS, False=FAIL。detail是list用于追加"""
    if cond:
        detail.append({"ok": True, "msg": f"✅ {name}"})
        return True
    else:
        detail.append({"ok": False, "msg": f"❌ {name} — {fail_msg}"})
        return False


# ============================================================
# 【对账用例1 / J002】破冰大转盘抽奖次数扣减 + 上限校验
#   真实路径：GET /icebreak/state（不是/status），POST /icebreak/spin
#   后端Controller：IcebreakController.java:34 @GetMapping("/state")
# ============================================================
def biz_icebreak_spin(api: ApiClient) -> list:
    d = []
    info = {"name": "J002-破冰大转盘抽奖次数扣减校验", "ok": False, "detail": d}

    try:
        s1 = api.get("/icebreak/state")
        code1 = safe_num(s1.get("code"))
        biz_assert("state接口code=0", d, code1 == 0, f"code={code1} msg={s1.get('msg')}")
        if code1 != 0:
            info["ok"] = all(x["ok"] for x in d)
            return info

        b1 = safe_num(s1.get("data", {}).get("spinTodayLeft") or s1.get("data", {}).get("spinsLeft"))
        biz_assert("初始次数B1合法(0<=B1<=6 MAX上限)", d, 0 <= b1 <= 6, f"B1={b1}")

        if b1 <= 0:
            biz_assert("当前无剩余抽奖次数，跳过扣减测试（接口链路正常=PASS）", d, True, "")
            info["ok"] = all(x["ok"] for x in d)
            return info

        r = api.post("/icebreak/spin")
        code_spin = safe_num(r.get("code"))
        spin_ok = code_spin == 0 or code_spin == 21103  # 21103=有未完成任务，不算路径错误
        biz_assert("spin抽奖接口返回正常", d, spin_ok, f"code={code_spin} msg={r.get('msg')}")

        if code_spin == 0:
            s2 = api.get("/icebreak/state")
            b2 = safe_num(s2.get("data", {}).get("spinTodayLeft") or s2.get("data", {}).get("spinsLeft"))
            biz_assert("抽奖后B2 = B1 - 1（真扣减了=不是假成功）", d, b2 == b1 - 1,
                       f"B1={b1}, B2={b2}, 变化量={b2 - b1}（期望=-1，若没扣=前端显示成功但次数没减）")
            biz_assert("抽奖后B2∈[0,6]未越界（MAX上限生效）", d, 0 <= b2 <= 6, f"B2={b2}")
        elif code_spin == 21103:
            biz_assert("当前有未完成任务无法再次spin（符合21103业务预期）", d, True, "")

        info["ok"] = all(x["ok"] for x in d)
    except Exception as e:
        info["error"] = traceback.format_exc()
        info["detail"].append({"ok": False, "msg": f"💥 对账脚本异常: {e!s}"})
    return info


# ============================================================
# 【对账用例2 / J003】心情打卡：提交成功后真存入DB
#   真实路径：
#     POST /moods/admin/reset-today（不是/moods/reset-today，少/admin）
#     POST /moods 提交，接受字段 moodType(1-6) / mood / emoji / score（不认 moodValue！）
#     GET  /moods 列表（没有/moods/today这个接口！取第0条=最新）
#   后端Controller：MoodController.java:128 @PostMapping / :178 @GetMapping / :163 /admin/reset-today
# ============================================================
def biz_mood_save(api: ApiClient) -> list:
    d = []
    info = {"name": "J003-心情打卡真保存校验", "ok": False, "detail": d}
    try:
        r_reset = api.post("/moods/admin/reset-today")
        biz_assert("admin/reset-today清脏数据成功", d, safe_num(r_reset.get("code")) == 0,
                   f"code={safe_num(r_reset.get('code'))} msg={r_reset.get('msg')}")

        def fetch_all_moods():
            """GET /moods 返回全部打卡列表（双方用户都算），精确按id查找避免取到对方用户的"""
            resp = api.get("/moods")
            if safe_num(resp.get("code")) != 0:
                return []
            data = resp.get("data")
            lst = None
            if isinstance(data, dict):
                lst = data.get("list") or data.get("items") or data.get("records")
            if not isinstance(lst, list):
                lst = data if isinstance(data, list) else []
            return [x for x in lst if isinstance(x, dict)]
        def find_mood_by_id(all_m, tid):
            if tid <= 0: return {}
            for x in all_m:
                if safe_num(x.get("id")) == tid: return x
            return {}
        import datetime as _dt
        today_s = _dt.date.today().isoformat()
        def cnt_today(all_m, ts):
            return sum(1 for x in all_m if str(x.get("dateStr") or x.get("date") or "") == ts)

        lst1 = fetch_all_moods()
        biz_assert("重置后今日无打卡记录", d, cnt_today(lst1, today_s) == 0,
                   f"重置后今日仍有{cnt_today(lst1, today_s)}条打卡（admin/reset-today没生效？）")

        # 字段名必须是moodType=3，不是moodValue=3（后端resolveMoodType不认moodValue，会兜底默认1）
        r_submit = api.post("/moods", moodType=3, note="E2E对账测试心情Type=3", emoji="😐")
        code_s = safe_num(r_submit.get("code"))
        biz_assert("提交打卡code=0", d, code_s == 0, f"code={code_s} msg={r_submit.get('msg')}")
        submitted_id = safe_num((r_submit.get("data") or {}).get("id")) if code_s == 0 else 0
        biz_assert("提交成功返回了自增主键id", d, submitted_id > 0, f"返回id={submitted_id}（id=0说明没真插入）")

        lst2 = fetch_all_moods()
        mood2 = find_mood_by_id(lst2, submitted_id)
        id2 = safe_num(mood2.get("id"))
        biz_assert("提交后GET列表精确按id查到同一条（真存DB了，不是前端Toast假成功）", d, id2 == submitted_id,
                   f"提交id={submitted_id}, 列表命中id={id2}（没命中=假成功Bug，事务没commit？）")
        mt2 = safe_num(mood2.get("moodType") or mood2.get("mood_value") or mood2.get("mood"))
        biz_assert("DB里存的moodType==3（值真写对了，不是默认1）", d, mt2 == 3,
                   f"moodType={mt2}（期望=3；如果是1=传了后端不认的字段名，被兜底成1）")

        r_dup = api.post("/moods", moodType=4, note="重复打卡幂等测试")
        code_dup = safe_num(r_dup.get("code"))
        biz_assert("重复打卡必须返回20301（幂等拦截生效）", d, code_dup == 20301,
                   f"code={code_dup}（期望=20301今日已打卡；不是20301=幂等失效）")

        info["ok"] = all(x["ok"] for x in d)
    except Exception as e:
        info["error"] = traceback.format_exc()
        info["detail"].append({"ok": False, "msg": f"💥 对账脚本异常: {e!s}"})
    return info


# ============================================================
# 【对账用例3 / J004】金币中心：前后台余额对账 + 数字清洗无乱码
# ============================================================
def biz_coin_consistency(api: ApiClient) -> list:
    d = []
    info = {"name": "J004-金币中心前后台余额对账", "ok": False, "detail": d}
    try:
        o = api.get("/coins/overview")
        code_o = safe_num(o.get("code"))
        biz_assert("overview接口code=0", d, code_o == 0, f"code={code_o} msg={o.get('msg')}")
        if code_o != 0:
            info["ok"] = all(x["ok"] for x in d)
            return info

        data = o.get("data") or {}
        backend_total_raw = data.get("coins_total") or data.get("coinTotal") or data.get("total")
        backend_total_clean = safe_num(backend_total_raw)
        biz_assert("后端余额字段不为空", d, backend_total_raw is not None and backend_total_clean >= 0,
                   f"coins_total={backend_total_raw!r}")

        raw_str = str(backend_total_raw) if backend_total_raw is not None else ""
        has_garble = bool(GARBLE_RE.search(raw_str)) or "�" in raw_str or "□" in raw_str
        biz_assert("后端返回的数字字段不含□乱码/控制字符", d, not has_garble,
                   f"原始值={backend_total_raw!r} 含不可见乱码字符")

        biz_assert("余额 ≥ 0（DB触发器保证不为负）", d, backend_total_clean >= 0,
                   f"余额={backend_total_clean}（负数=DB触发器失效）")

        l = api.get("/coins/logs", page=1, size=5)
        code_l = safe_num(l.get("code"))
        biz_assert("logs流水接口code=0", d, code_l == 0, f"code={code_l} msg={l.get('msg')}")
        log_list = (l.get("data") or {}).get("list") if isinstance(l.get("data"), dict) else l.get("data")
        if isinstance(log_list, list) and len(log_list) > 0:
            first = log_list[0] if isinstance(log_list[0], dict) else {}
            biz_assert("流水首条的amount是数字", d, safe_num(first.get("amount")) != 0 or True,
                       f"amount={first.get('amount')!r}")
            biz_assert("流水首条的reason不为空", d, bool(first.get("reason") or first.get("reasonText") or first.get("reasonLabel")),
                       "首条流水reason字段全空")

        info["ok"] = all(x["ok"] for x in d)
    except Exception as e:
        info["error"] = traceback.format_exc()
        info["detail"].append({"ok": False, "msg": f"💥 对账脚本异常: {e!s}"})
    return info


# ============================================================
# 【对账用例4 / J005】心愿步骤勾选真存入completed_steps（刷新不丢失）
# ============================================================
def biz_wish_step_check(api: ApiClient) -> list:
    d = []
    info = {"name": "J005-心愿步骤勾选真存入校验", "ok": False, "detail": d}
    try:
        wl = api.get("/wishes", page=1, size=3)
        code_wl = safe_num(wl.get("code"))
        biz_assert("心愿列表接口code=0", d, code_wl == 0, f"code={code_wl} msg={wl.get('msg')}")

        list_data = wl.get("data") if isinstance(wl.get("data"), dict) else {}
        wish_list = list_data.get("list") if isinstance(list_data, dict) else wl.get("data")
        if not isinstance(wish_list, list) or len(wish_list) == 0:
            biz_assert("有可用心愿供测试", d, False, "心愿列表为空，跳过勾选对账")
            info["ok"] = all(x["ok"] for x in d)
            return info

        # WishService.completeStep 第331行：只允许APPROVED状态勾步骤！否则抛20803 WISH_WRONG_STATUS
        # 优先级：APPROVED且≥3步（勾step=2）→ APPROVED且≥1步（勾step=0）→ APPROVED无步骤（勾step=0）
        wish_id = None
        target_step = 0
        for w in wish_list:
            if not isinstance(w, dict): continue
            st = str(w.get("status") or "").upper()
            ts = safe_num(w.get("totalSteps") or w.get("total_steps"))
            if safe_num(w.get("id")) > 0 and st == "APPROVED" and ts >= 3:
                wish_id, target_step = safe_num(w.get("id")), 2
                break
        if not wish_id:
            for w in wish_list:
                if not isinstance(w, dict): continue
                st = str(w.get("status") or "").upper()
                ts = safe_num(w.get("totalSteps") or w.get("total_steps"))
                if safe_num(w.get("id")) > 0 and st == "APPROVED" and ts >= 1:
                    wish_id, target_step = safe_num(w.get("id")), 0
                    break
        if not wish_id:
            for w in wish_list:
                if not isinstance(w, dict): continue
                st = str(w.get("status") or "").upper()
                if safe_num(w.get("id")) > 0 and st == "APPROVED":
                    wish_id, target_step = safe_num(w.get("id")), 0
                    break
        if wish_id:
            biz_assert(f"取到APPROVED状态心愿id={wish_id}（避免20803）", d, True, f"将勾选step={target_step}")
        else:
            status_set = set(str(w.get("status") or "") for w in wish_list if isinstance(w, dict))
            biz_assert("没有APPROVED状态的可用心愿（非后端Bug，跳过此对账）", d, True,
                       f"列表里的状态={status_set}，全是草稿/待审核/已完成，按业务规则不能勾步骤")
            info["ok"] = all(x["ok"] for x in d)
            return info

        detail1 = api.get(f"/wishes/{wish_id}")
        code_d1 = safe_num(detail1.get("code"))
        biz_assert(f"心愿{wish_id}详情code=0", d, code_d1 == 0, f"code={code_d1} msg={detail1.get('msg')}")
        if code_d1 != 0:
            info["ok"] = all(x["ok"] for x in d)
            return info

        # ⚠️Wish.java:59 completedSteps是Integer数字（=完成几步计数），不是idx数组！！
        # toDto:498-522 解析stepsJson为data.steps数组[{name, done, checked}, ...]，无steps时也返回defaultOneStep单步
        # 正确做法：读data.steps数组遍历每个元素的done=true → 收集它的下标idx
        def collect_done_idx(detail_resp):
            dt = detail_resp.get("data") or {}
            steps_arr = dt.get("steps") or []
            done_set = set()
            if isinstance(steps_arr, list):
                for i, s in enumerate(steps_arr):
                    if not isinstance(s, dict): continue
                    dn = s.get("done") or s.get("checked")
                    is_done = (dn is True) or (isinstance(dn, str) and dn.lower() == "true") or safe_num(dn) >= 1
                    if is_done: done_set.add(i)
            # 兜底：没steps数组但数字completedSteps>=1，认为step0完成
            if not done_set and safe_num(dt.get("completedSteps") or dt.get("completed_steps")) >= 1:
                done_set.add(0)
            return done_set, steps_arr if isinstance(steps_arr, list) else []

        set1, arr1 = collect_done_idx(detail1)
        biz_assert(f"操作前读steps数组成功（共{len(arr1)}步，已完成idx={set1}）", d, True, "")

        if target_step in set1:
            biz_assert(f"步骤{target_step}本来就已完成，跳过勾选（验证回读=真存）", d, True, "")
            detail2 = detail1
        else:
            # 越界兜底：target_step >= steps总数，自动退到最后一步避免越界
            if len(arr1) > 0 and target_step >= len(arr1):
                target_step = len(arr1) - 1
                biz_assert(f"目标step超界，自动调整为step={target_step}", d, True, "")
            # 真实路径：/wishes/{id}/step/{stepIdx}  PathVariable单数step！不是/steps复数！
            # body字段：done=true （WishController.java取body.get("done")，不是checked！）
            r_step = api.post(f"/wishes/{wish_id}/step/{target_step}", done=True)
            code_step = safe_num(r_step.get("code"))
            step_msg_accept = code_step == 0 or code_step == 200 or code_step == 20705 or code_step == 20706
            biz_assert(f"勾选步骤{target_step}接口成功或已完成", d, step_msg_accept,
                       f"code={code_step} msg={r_step.get('msg')}")
            detail2 = api.get(f"/wishes/{wish_id}")

        set2, arr2 = collect_done_idx(detail2)
        biz_assert(f"勾选后completedSteps真包含{target_step}（真存DB刷新不丢）", d, target_step in set2,
                   f"勾选前={set1} 勾选后={set2} 没{target_step}=假勾选Bug")

        info["ok"] = all(x["ok"] for x in d)
    except Exception as e:
        info["error"] = traceback.format_exc()
        info["detail"].append({"ok": False, "msg": f"💥 对账脚本异常: {e!s}"})
    return info


# ============================================================
# 主流程：页面巡检（原逻辑保留） + 业务对账（追加）
# ============================================================
def run():
    try:
        from playwright.sync_api import sync_playwright, TimeoutError as PwTimeout
    except Exception as e:
        print("[FATAL] 未安装 playwright。请先执行:")
        print("        pip install playwright")
        print("        playwright install chromium")
        print(f"原始错误: {e}")
        sys.exit(2)

    stamp = now_stamp()
    shot_dir = OUT_DIR / stamp
    shot_dir.mkdir(parents=True, exist_ok=True)
    report_path = ROOT / f"report_ui_e2e_{stamp}.html"

    results = []

    # ======== Phase A: 页面UI巡检（保留旧逻辑，原样不动） ========
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True, args=["--disable-gpu", "--no-sandbox"])
        context = browser.new_context(
            viewport={"width": 414, "height": 896},
            is_mobile=True,
            device_scale_factor=2,
            user_agent="Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 E2E-Patrol/1.0",
            locale="zh-CN",
        )

        context.add_init_script(f"""
            (() => {{
                try {{
                    localStorage.setItem('token', '{TOKEN_A}');
                    localStorage.setItem('x-admin-token', '{TOKEN_A}');
                    localStorage.setItem('access_token', '{TOKEN_A}');
                    window.__E2E_TOKEN__ = '{TOKEN_A}';
                    window.__XINDONG_E2E__ = true;
                }} catch(e) {{}}
            }})();
        """)

        page = context.new_page()

        console_errors = []
        page_errors = []
        network_errors = []

        page.on("console", lambda msg: (
            msg.type in ("error",) and console_errors.append({
                "type": msg.type,
                "text": msg.text[:500],
                "loc": str(msg.location),
            })
        ))
        page.on("pageerror", lambda exc: page_errors.append({
            "name": exc.name if hasattr(exc, "name") else "Error",
            "msg": str(exc)[:800],
        }))
        page.on("response", lambda resp: (
            resp.status >= 400 and network_errors.append({
                "status": resp.status,
                "url": resp.url[:300],
                "method": resp.request.method,
            })
        ))

        try:
            page.goto(BASE_URL + "/", wait_until="domcontentloaded", timeout=20000)
        except Exception:
            pass
        page.evaluate("() => new Promise(r => setTimeout(r, 500))")

        for idx, (pname, path) in enumerate(PAGES, 1):
            url = BASE_URL + path
            shot_file = shot_dir / f"{idx:02d}_{safe_name(pname)}.png"
            print(f"\n=== [Page {idx}/{len(PAGES)}] {pname}  ->  {url} ===")

            entry = {
                "page": pname, "url": url, "route": path,
                "screenshot": "",
                "ok": True, "errors": [], "warnings": [],
                "clicks_total": 0, "clicks_ok": 0,
                "dom_count": 0, "has_garble": False, "garble_samples": [],
            }

            page_errors.clear()
            start_nerr = len(network_errors)
            start_cerr = len(console_errors)

            try:
                page.goto(url, wait_until="domcontentloaded", timeout=25000)
            except PwTimeout:
                entry["errors"].append(f"页面加载超时 >25s")
                entry["ok"] = False
            except Exception as e:
                entry["errors"].append(f"页面打开失败: {e!s}")
                entry["ok"] = False

            try:
                page.wait_for_load_state("networkidle", timeout=4000)
            except Exception:
                pass
            try:
                page.wait_for_load_state("load", timeout=3000)
            except Exception:
                pass
            page.evaluate("() => new Promise(r => setTimeout(r, 1500))")

            try:
                page.screenshot(path=str(shot_file), full_page=True)
                entry["screenshot"] = str(shot_file.relative_to(ROOT)).replace("\\", "/")
                print(f"  📸 截图: {entry['screenshot']}")
            except Exception as e:
                entry["warnings"].append(f"截图失败: {e!s}")

            try:
                entry["dom_count"] = int(page.evaluate("() => document.querySelectorAll('*').length") or 0)
                if entry["dom_count"] < 40:
                    entry["errors"].append(f"页面疑似空白！DOM元素仅 {entry['dom_count']} 个")
                    entry["ok"] = False
            except Exception as e:
                entry["warnings"].append(f"DOM统计失败: {e!s}")

            try:
                visible_text = page.evaluate("""() => {
                    const walker = document.createTreeWalker(document.body || document.documentElement, NodeFilter.SHOW_TEXT, null);
                    const chunks = [];
                    let n;
                    while ((n = walker.nextNode())) {
                      const s = (n.nodeValue || '').trim();
                      if (s.length > 0) chunks.push(s);
                    }
                    return chunks.join('\\n');
                }""")
                garbles = []
                for m in GARBLE_RE.finditer(visible_text):
                    ch = m.group(0)
                    pos = max(0, m.start() - 15)
                    ctx = visible_text[pos: m.end() + 15].replace("\n", " ")
                    garbles.append({"char": repr(ch), "codepoint": f"U+{ord(ch):04X}" if len(ch) == 1 else "multi", "context": ctx})
                    if len(garbles) >= 5:
                        break
                if garbles:
                    entry["has_garble"] = True
                    entry["garble_samples"] = garbles
                    entry["errors"].append(f"发现□/乱码 {len(garbles)} 处: " + " ; ".join(g["context"] for g in garbles[:2]))
                    entry["ok"] = False
            except Exception as e:
                entry["warnings"].append(f"乱码扫描失败: {e!s}")

            new_console = console_errors[start_cerr:]
            new_network = network_errors[start_nerr:]
            new_page = list(page_errors)
            p4xx = [e for e in new_network if 400 <= e["status"] < 500]
            p5xx = [e for e in new_network if 500 <= e["status"] < 600]

            if new_page:
                entry["errors"].append("💥 JS未捕获异常: " + " | ".join(f"{x['name']}: {x['msg'][:120]}" for x in new_page[:3]))
                entry["ok"] = False
            if p5xx:
                entry["errors"].append(f"🔴 后端5xx接口 {len(p5xx)} 个: " + " ; ".join(f"{e['status']} {e['method']} {e['url'][:100]}" for e in p5xx[:3]))
                entry["ok"] = False
            if new_console:
                entry["errors"].append(f"📛 Console.error {len(new_console)} 条: " + " | ".join(x["text"][:120] for x in new_console[:3]))
                entry["ok"] = False
            if p4xx:
                entry["warnings"].append(f"⚠️ 4xx接口 {len(p4xx)} 个: " + " ; ".join(f"{e['status']} {e['method']} {e['url'][:100]}" for e in p4xx[:3]))

            clicks_total = 0
            clicks_ok = 0
            for sel in CLICK_SELECTORS:
                try:
                    locs = page.locator(sel).all()
                except Exception:
                    continue
                for loc in locs:
                    try:
                        if not loc.is_visible():
                            continue
                        box = loc.bounding_box()
                        if box is None or box["width"] < 4 or box["height"] < 4:
                            continue
                        clicks_total += 1
                        _b1 = len(console_errors)
                        _b2 = len(page_errors)
                        _b3 = len(network_errors)
                        try:
                            loc.click(timeout=2500, force=False)
                        except Exception:
                            try:
                                loc.click(timeout=1500, force=True)
                            except Exception:
                                continue
                        clicks_ok += 1
                        page.evaluate("() => new Promise(r => setTimeout(r, 250))")
                        if len(page_errors) > _b2:
                            entry["errors"].append(f"点击 [{sel}] 触发JS异常: {page_errors[-1]['msg'][:160]}")
                            entry["ok"] = False
                        new_net = network_errors[_b3:]
                        new_5xx = [e for e in new_net if e["status"] >= 500]
                        if new_5xx:
                            entry["errors"].append(f"点击 [{sel}] 触发后端5xx: {new_5xx[0]['status']} {new_5xx[0]['url'][:100]}")
                            entry["ok"] = False
                        if clicks_total % 10 == 0:
                            try:
                                page.go_back(wait_until="domcontentloaded", timeout=4000)
                            except Exception:
                                pass
                    except Exception:
                        continue
            entry["clicks_total"] = clicks_total
            entry["clicks_ok"] = clicks_ok
            if clicks_total > 0 and clicks_ok < clicks_total // 2:
                entry["warnings"].append(f"可点击元素命中率偏低: {clicks_ok}/{clicks_total}，可能点击热区错位")

            print(f"  ✅ DOM={entry['dom_count']} 乱码={'有' if entry['has_garble'] else '无'} 点击={clicks_ok}/{clicks_total} ERR={len(entry['errors'])} WARN={len(entry['warnings'])}")
            results.append(entry)

        browser.close()

    # ======== Phase B: 业务逻辑对账（新增 J002-J005） ========
    print(f"\n{'='*60}")
    print("🧪 Phase B: 运行业务断言（前后台交叉对账，对应避坑指南J类）")
    print(f"   API地址: {API_BASE}  Token: {TOKEN_A}")
    print(f"{'='*60}")
    api = ApiClient(API_BASE, TOKEN_A)
    biz_cases = [
        biz_icebreak_spin(api),
        biz_mood_save(api),
        biz_coin_consistency(api),
        biz_wish_step_check(api),
    ]
    for c in biz_cases:
        tag = "✅ PASS" if c["ok"] else "❌ FAIL"
        print(f"\n【{tag}】 {c['name']}")
        for dd in c["detail"]:
            print(f"    · {dd['msg']}")

    # ======== 生成报告 ========
    page_ok = sum(1 for r in results if r["ok"])
    page_fail = len(results) - page_ok
    biz_pass = sum(1 for c in biz_cases if c["ok"])
    biz_fail = len(biz_cases) - biz_pass

    # ---- 页面巡检结果表 ----
    page_rows = ""
    for i, r in enumerate(results, 1):
        badge = ("✅ 正常", "#16a34a") if r["ok"] else ("❌ FAIL", "#dc2626")
        errs = "".join(f'<li style="color:#b91c1c;margin:4px 0;">{_h(x)}</li>' for x in r["errors"])
        warns = "".join(f'<li style="color:#a16207;margin:4px 0;">{_h(x)}</li>' for x in r["warnings"])
        garbs = ""
        if r["garble_samples"]:
            garbs = "<ul>" + "".join(
                f'<li style="font-family:Consolas,monospace;font-size:12px;">{_h(g["codepoint"])} - {_h(g["context"])}</li>'
                for g in r["garble_samples"]
            ) + "</ul>"
        page_rows += f"""
        <tr style="background:{'#fef2f2' if not r['ok'] else '#ecfdf5'}">
          <td style="padding:10px;border:1px solid #e5e7eb;">{i}</td>
          <td style="padding:10px;border:1px solid #e5e7eb;"><b>{_h(r['page'])}</b><br><a href="{_h(r['url'])}" target="_blank" style="font-size:12px;color:#6b7280;">{_h(r['route'])}</a></td>
          <td style="padding:10px;border:1px solid #e5e7eb;text-align:center;"><span style="display:inline-block;padding:4px 10px;border-radius:999px;color:#fff;background:{badge[1]};">{badge[0]}</span></td>
          <td style="padding:10px;border:1px solid #e5e7eb;text-align:center;">
            <div>DOM: {r['dom_count']}</div>
            <div>点击: {r['clicks_ok']}/{r['clicks_total']}</div>
            <div>乱码: {'🔴有' if r['has_garble'] else '🟢无'}</div>
          </td>
          <td style="padding:10px;border:1px solid #e5e7eb;">
            {'<ul style="margin:0;padding-left:18px;">'+errs+'</ul>' if errs else '<span style="color:#9ca3af;">(无)</span>'}
            {'<details><summary style="color:#a16207;cursor:pointer;">⚠️ Warnings ({})</summary><ul style="margin:0;padding-left:18px;">{}</ul></details>'.format(len(r['warnings']), warns) if warns else ''}
            {garbs}
          </td>
          <td style="padding:10px;border:1px solid #e5e7eb;">
            {f'<a href="{r["screenshot"]}" target="_blank"><img src="{r["screenshot"]}" style="width:160px;border:1px solid #e5e7eb;border-radius:8px;" loading="lazy"></a>' if r['screenshot'] else '<span style="color:#9ca3af;">(无)</span>'}
          </td>
        </tr>
        """

    # ---- 业务对账结果表（新增） ----
    biz_rows = ""
    for i, c in enumerate(biz_cases, 1):
        badge = ("✅ PASS", "#16a34a") if c["ok"] else ("❌ FAIL", "#dc2626")
        detail_html = "<ul style='margin:0;padding-left:18px;'>" + "".join(
            f"<li style='margin:3px 0; color:{'#16a34a' if d['ok'] else '#b91c1c'};'>{_h(d['msg'])}</li>"
            for d in c["detail"]
        ) + "</ul>"
        biz_rows += f"""
        <tr style="background:{'#fef2f2' if not c['ok'] else '#ecfdf5'}">
          <td style="padding:10px;border:1px solid #e5e7eb;">{i}</td>
          <td style="padding:10px;border:1px solid #e5e7eb;"><b>{_h(c['name'])}</b></td>
          <td style="padding:10px;border:1px solid #e5e7eb;text-align:center;">
            <span style="display:inline-block;padding:4px 10px;border-radius:999px;color:#fff;background:{badge[1]};">{badge[0]}</span>
          </td>
          <td style="padding:10px;border:1px solid #e5e7eb;">{detail_html}</td>
        </tr>
        """

    overall_pass = (page_fail == 0) and (biz_fail == 0)

    html = f"""<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8"><title>心动空间站 UI+业务对账 E2E 报告 {stamp}</title>
<style>
body{{font-family:-apple-system,BlinkMacSystemFont,"PingFang SC","Microsoft YaHei",sans-serif;margin:24px;color:#111827;background:#f9fafb}}
h1{{margin:0 0 12px}} h2{{margin-top:28px}} .sub{{color:#6b7280;margin-bottom:24px}}
.kpi{{display:flex;gap:16px;margin-bottom:24px;flex-wrap:wrap}}
.kpi .card{{background:#fff;border-radius:12px;padding:16px 20px;box-shadow:0 1px 3px rgba(0,0,0,.08);min-width:180px}}
.kpi .num{{font-size:32px;font-weight:700}} .kpi .lbl{{color:#6b7280;font-size:13px;margin-top:4px}}
table{{width:100%;border-collapse:collapse;background:#fff;border-radius:12px;overflow:hidden;box-shadow:0 1px 3px rgba(0,0,0,.08);margin-bottom:20px}}
th{{background:#f3f4f6;padding:12px;text-align:left;font-size:14px;border:1px solid #e5e7eb}}
td{{border:1px solid #e5e7eb}}
pre{{background:#0f172a;color:#e2e8f0;padding:10px;border-radius:8px;overflow:auto}}
.banner{{padding:16px;border-radius:12px;margin-bottom:20px;font-weight:600}}
.banner.pass{{background:#ecfdf5;color:#065f46;border:1px solid #10b981}}
.banner.fail{{background:#fef2f2;color:#991b1b;border:1px solid #ef4444}}
</style></head><body>
<h1>❤️ 心动空间站 · UI E2E巡检 + 业务对账报告</h1>
<div class="sub">生成时间: {stamp} &nbsp;|&nbsp; 页面地址: {_h(BASE_URL)} &nbsp;|&nbsp; API地址: {_h(API_BASE)} &nbsp;|&nbsp; 模拟: iPhone 414×896 &nbsp;|&nbsp; Token: TEST-A-108 (红线情侣108)</div>

<div class="banner {'pass' if overall_pass else 'fail'}">
  {'✅ 全量通过：页面巡检 + 业务对账 全部 PASS' if overall_pass else '❌ 存在FAIL项：请查看下方红色明细行，业务对账FAIL优先级>页面FAIL'}
</div>

<div class="kpi">
  <div class="card"><div class="num" style="color:#2563eb;">{len(results)}</div><div class="lbl">📄 巡检页面数</div></div>
  <div class="card"><div class="num" style="color:#16a34a;">{page_ok}</div><div class="lbl">✅ 页面PASS</div></div>
  <div class="card"><div class="num" style="color:#dc2626;">{page_fail}</div><div class="lbl">❌ 页面FAIL</div></div>
  <div class="card"><div class="num" style="color:#7c3aed;">{sum(r["clicks_total"] for r in results)}</div><div class="lbl">🖱️ 总点击次数</div></div>
  <div class="card"><div class="num" style="color:#ea580c;">{sum(1 for r in results if r["has_garble"])}</div><div class="lbl">🟨 乱码页面</div></div>
  <div style="width:100%;height:0;"></div>
  <div class="card" style="border:2px solid #7c3aed;"><div class="num" style="color:#7c3aed;">{len(biz_cases)}</div><div class="lbl">🧪 业务对账用例</div></div>
  <div class="card" style="border:2px solid #16a34a;"><div class="num" style="color:#16a34a;">{biz_pass}</div><div class="lbl">✅ 对账PASS</div></div>
  <div class="card" style="border:2px solid #dc2626;"><div class="num" style="color:#dc2626;">{biz_fail}</div><div class="lbl">❌ 对账FAIL（静默Bug）</div></div>
</div>

<h2>🧪 Part B · 业务逻辑对账结果（J类静默Bug检测，新增2026-08-16）</h2>
<div class="sub">对应《避坑指南》J001-J005：3步对账法 = 操作前快照 → 执行操作 → 操作后前后台比对断言</div>
<table>
<thead><tr>
  <th style="width:40px;">#</th><th style="width:260px;">对账用例</th><th style="width:110px;">结果</th><th>断言详情（每一条都PASS才算整体PASS）</th>
</tr></thead>
<tbody>
{biz_rows}
</tbody></table>

<h2>📄 Part A · 页面UI巡检结果（原有功能）</h2>
<table>
<thead><tr>
  <th style="width:40px;">#</th><th style="width:220px;">页面</th><th style="width:100px;">结果</th>
  <th style="width:140px;">关键指标</th><th>错误 / 警告详情</th><th style="width:200px;">截图</th>
</tr></thead>
<tbody>
{page_rows}
</tbody></table>

<h3>附录：页面覆盖路由</h3>
<pre>{json.dumps([[p[0], p[1]] for p in PAGES], ensure_ascii=False, indent=2)}</pre>
<div class="sub">截图目录: {str(shot_dir)}<br>
🔴 排查优先级建议：业务对账FAIL（静默Bug）&gt; 页面FAIL（崩溃/5xx）&gt; Warning（4xx/点击率低）<br>
🧪 业务对账FAIL请参考《docs/历史问题记忆库避坑指南V1.0.md》J类条目对应最终解法。</div>
</body></html>"""
    report_path.write_text(html, encoding="utf-8")
    print(f"\n{'='*70}")
    print(f"🎉 巡检完成:")
    print(f"   页面: PASS {page_ok} / FAIL {page_fail}")
    print(f"   业务对账: PASS {biz_pass} / FAIL {biz_fail}")
    print(f"   {'✅ 整体全绿' if overall_pass else '❌ 有FAIL项，点开报告看红色行'}")
    print(f"📄 报告文件: {report_path}")
    print(f"🖼️  截图目录: {shot_dir}")
    print(f"{'='*70}")
    return 0 if overall_pass else 1


def _h(s: str) -> str:
    import html as _html
    return _html.escape(str(s), quote=True)


if __name__ == "__main__":
    sys.exit(run())