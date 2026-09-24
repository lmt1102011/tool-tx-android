"""
fb.py — Firebase Realtime Database helpers.
"""
def picks(user_data):
    if not user_data:
        return 0
    return int(user_data.get("balanceFields", 0))
