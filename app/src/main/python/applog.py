"""
applog.py — Ring buffer log toàn app (đọc từ Settings để xem lỗi).
Chạy trong tiến trình Python của Chaquopy; an toàn đa luồng.
"""

import threading
import time

_MAX = 500
_buf = []
_lock = threading.Lock()


def log(tag, msg):
    global _buf
    try:
        line = "[%s] [%s] %s" % (time.strftime("%H:%M:%S"), tag, str(msg))
        with _lock:
            _buf.append(line)
            if len(_buf) > _MAX:
                _buf = _buf[-_MAX:]
    except Exception:
        pass


def dump(limit=_MAX):
    with _lock:
        return "\n".join(_buf[-limit:])


def clear():
    with _lock:
        del _buf[:]