def device(ua: str | None) -> str:
    """User-Agent → 'iPhone · Safari' 같은 짧은 이름"""
    if not ua:
        return "알 수 없는 기기"
    os_ = ("iPhone" if "iPhone" in ua else "iPad" if "iPad" in ua else "Android" if "Android" in ua else "Mac" if "Mac OS X" in ua
           else "Windows" if "Windows" in ua else "Linux" if "Linux" in ua else "기기")
    br = ("Edge" if "Edg/" in ua else "Chrome" if "Chrome/" in ua else "Firefox" if "Firefox/" in ua else "Safari" if "Safari/" in ua else "브라우저")
    return f"{os_} · {br}"
