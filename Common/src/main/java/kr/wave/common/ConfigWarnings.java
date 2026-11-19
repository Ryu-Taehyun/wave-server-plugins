package kr.wave.common;

import java.util.logging.Logger;

/**
 * 설정 · 데이터 파일 오류 처리 기준 (PremiumPass · DailyShop 공통)
 * <ul>
 *   <li>설정 · 데이터 파일에 잘못된 값 → 콘솔에 같은 형식으로 경고하고 기본값으로 대체 (서버는 계속 동작)</li>
 *   <li>/reload 같은 관리자 명령으로 다시 읽었으면 → 경고 개수를 명령한 관리자에게도 알림</li>
 *   <li>관리자가 명령어 · GUI 로 바꾼 내용의 저장 실패 → 콘솔 오류 + 그 관리자에게 안내</li>
 *   <li>명령어 입력 오류 (숫자 아님 등) → 입력한 사람에게만 안내</li>
 * </ul>
 */
public final class ConfigWarnings {

    private final Logger log;
    private int count;

    public ConfigWarnings(Logger log) {
        this.log = log;
    }

    /** 다시 읽기 전에 개수를 0 으로 */
    public void reset() {
        count = 0;
    }

    public int count() {
        return count;
    }

    /** "파일: 항목 값이 올바르지 않습니다 (값). 대체 동작" */
    public void invalid(String file, String what, Object value, String fallback) {
        count++;
        log.warning(file + ": " + what + " 값이 올바르지 않습니다 (" + value + "). " + fallback);
    }

    /** reload 명령 결과 안내 문구 */
    public String reloadMessage() {
        return count == 0 ? "&a설정을 다시 불러왔습니다."
                : "&e설정을 다시 불러왔습니다. 잘못된 값 " + count + "개는 기본값으로 대신했습니다. &7(서버 콘솔에서 확인)";
    }
}
