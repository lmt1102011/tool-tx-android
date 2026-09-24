"""
agent.py — Agent control cho Chaquopy (APK).

Mô hình WebView nhúng (không cần fork Cromite / CDP 9222):
- Game Sunwin mở ngay trong WebView của app (webViewBridge.loadUrl).
- Python nối /agent-ws tới server bằng mã liên kết, nhận {t:'eval',id,expr},
  chạy expr ngay trong WebView nhúng (evalJs) rồi trả {t:'res',id,value}.
"""

import json
import sys
import threading
import time

try:
    import websocket
    _HAS_WS = True
except Exception:
    _HAS_WS = False

def _sslopt_list():
    """Các sslopt để thử lần lượt cho websocket-client trên Chaquopy.
    Chaquopy không tự tìm CA bundle như requests; thử certifi, rồi
    không verify để đảm bảo nối được qua tunnel Cloudflare."""
    import ssl
    opts = []
    try:
        import certifi
        opts.append({"ca_certs": certifi.where()})
    except Exception:
        pass
    opts.append({"cert_reqs": ssl.CERT_NONE, "check_hostname": False})
    return opts

_running = False
_stop_evt = threading.Event()
_lock = threading.Lock()
_ws_lock = threading.Lock()
_ws = None
_last_status = {"connected": False, "message": "", "url": "", "running": False}

RECONNECT_DELAY = 5
SERVER_TIMEOUT = 25
EVAL_TIMEOUT_MS = 12000


def log(msg, err=False):
    try:
        import applog
        applog.log("agent", ("ERR " if err else "") + str(msg))
    except Exception:
        pass
    try:
        import logging
        if err:
            logging.getLogger("agent").warning(msg)
        else:
            logging.getLogger("agent").info(msg)
    except Exception:
        pass
    try:
        print("[agent] " + msg)
    except Exception:
        pass


def _set_status(connected, message=None, url=None, running=None):
    with _lock:
        _last_status["connected"] = bool(connected)
        if message is not None:
            _last_status["message"] = str(message)
        if url is not None:
            _last_status["url"] = str(url)
        if running is not None:
            _last_status["running"] = bool(running)


def status():
    with _lock:
        return dict(_last_status)


def is_running():
    return _running


def _ws_url(server):
    s = str(server or "").strip().rstrip("/")
    scheme = "wss://" if s.startswith("https://") else "ws://"
    return scheme + s.replace("https://", "").replace("http://", "") + "/agent-ws"


def _send(msg):
    global _ws
    with _ws_lock:
        w = _ws
        if w:
            try:
                w.send(json.dumps(msg))
                return True
            except Exception:
                pass
    return False


def _eval_in_webview(expr, timeout_ms=EVAL_TIMEOUT_MS):
    """Chạy expr trong WebView nhúng. Trả (ok, value); ok=False khi có lỗi/timeout."""
    try:
        from com.lmt.tooltx import WebViewBridge
        raw = WebViewBridge.evalJs(expr, timeout_ms)
    except Exception as e:
        return False, str(e)
    if raw is None:
        return False, "timeout"
    try:
        return True, json.loads(raw)
    except Exception:
        return False, "bad-json:" + str(raw)[:200]


def _handle_eval(req):
    try:
        rid = str(req.get("id") or "")
        expr = str(req.get("expr") or "")
        from com.lmt.tooltx import WebViewBridge as _wvb
        try:
            _cu = _wvb.currentUrl()
        except Exception:
            _cu = None
        log("eval url=%s nhu cau" % (str(_cu)[:80]))
        ok, value = _eval_in_webview(expr)
        if not ok:
            log("eval LỖI: " + str(value)[:200])
            _send({"t": "res", "id": rid, "value": [], "error": str(value)[:200]})
        elif isinstance(value, list):
            log("eval OK: %d item" % len(value))
            _send({"t": "res", "id": rid, "value": value})
        else:
            _send({"t": "res", "id": rid, "value": []})
    except Exception as e:
        try:
            _send({"t": "res", "id": str(req.get("id") or ""), "value": [], "error": str(e)[:200]})
        except Exception:
            pass


def _try_connect(url):
    """Thử connect websocket với từng sslopt. Trả ws hoặc ném exception."""
    import traceback
    last_err = None
    log("Try connect WS: " + url)
    for sopt in _sslopt_list():
        try:
            w = websocket.create_connection(
                url, timeout=SERVER_TIMEOUT, ping_interval=20, ping_timeout=15,
                sslopt=sopt
            )
            log("WS connected OK (sslopt=%s)", sopt)
            return w
        except Exception as e:
            last_err = e
            log("Nối thử (sslopt=%s) thất bại: %s — %s" % (sopt, str(e)[:200], traceback.format_exc()[-500:]), err=True)
    raise last_err


def _server_main(server, code):
    global _ws, _running
    fatal = False
    log("_server_main enter: server=%s code=%s has_ws=%s" % (server, code, _HAS_WS))
    while not _stop_evt.is_set():
        url = _ws_url(server)
        try:
            if not _HAS_WS:
                raise RuntimeError("thiếu thư viện websocket-client")
            _set_status(False, "Nối server: %s..." % (server or ""))
            w = _try_connect(url)
        except Exception as e:
            _set_status(False, "Lỗi nối server: %s" % str(e)[:160])
            log("Lỗi nối server: " + str(e)[:160] + " — thử lại trong %ds" % RECONNECT_DELAY, err=True)
            _stop_evt.wait(RECONNECT_DELAY)
            continue
        with _ws_lock:
            _ws = w
        try:
            w.send(json.dumps({"t": "hello", "code": code, "v": 1, "node": sys.version.split()[0]}))
            _set_status(False, "Đã gửi mã liên kết — chờ server xác nhận...")
            while not _stop_evt.is_set():
                w.settimeout(0.5)
                try:
                    raw = w.recv()
                except websocket.WebSocketTimeoutException:
                    raw = None
                except Exception:
                    break
                if not raw:
                    continue
                try:
                    m = json.loads(raw)
                except Exception:
                    continue
                if not m.get("t"):
                    continue
                t = m["t"]
                if t == "ok":
                    game_url = str(m.get("url") or "")
                    _set_status(True, "Đã kết nối — bấm MỞ GAME để mở game.", game_url or None)
                    log("Server xác nhận: uid=%s" % str(m.get("uid", ""))[:8])
                elif t == "err":
                    msg = str(m.get("message") or "Server từ chối.")
                    _set_status(True, msg)
                    log("Server từ chối: " + msg, err=True)
                    fatal = True
                    try:
                        w.close()
                    except Exception:
                        pass
                    break
                elif t == "ping":
                    _send({"t": "pong", "ts": int(time.time() * 1000)})
                elif t == "eval":
                    _handle_eval(m)
        finally:
            try:
                w.close()
            except Exception:
                pass
            with _ws_lock:
                _ws = None
        if fatal:
            break
        if _stop_evt.is_set():
            break
        _set_status(False, "Mất kết nối server — thử lại trong %ds..." % RECONNECT_DELAY)
        _stop_evt.wait(RECONNECT_DELAY)
    _running = False
    if not fatal:
        _set_status(False, "Agent đã dừng.", running=False)
    else:
        _set_status(True, _last_status.get("message", "Server từ chối."), running=False)


def start_agent(server_url, code):
    global _running
    invalid = False
    with _lock:
        if _running:
            return True
        code = str(code or "").strip().upper()
        if not server_url or not code:
            _running = False
            invalid = True
        else:
            _running = True
            _stop_evt.clear()
    if invalid:
        _set_status(False, "Thiếu server/code — không bắt đầu.", running=False)
        return False
    _set_status(True, "Agent đang khởi động...", running=True)
    threading.Thread(target=_server_main, args=(server_url, code), daemon=True).start()
    return True


def stop():
    global _running
    with _lock:
        _running = False
    _stop_evt.set()
    with _ws_lock:
        w = _ws
        if w:
            try:
                w.close()
            except Exception:
                pass


# Tương thích API cũ (PythonBridge.startFork vẫn gọi tới).
def start_fork(server_url, code):
    return start_agent(server_url, code)