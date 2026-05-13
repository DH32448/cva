package weep12.cva.service.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

/**
 * 验证码服务——生成、存储、校验。
 *
 * <p>使用 Redis 存储验证码，Key 格式: {@code code:{type}:{email}}
 * TTL 5 分钟，同一邮箱 60 秒内不可重复发送。</p>
 */
@Slf4j
@Service
public class VerificationCodeService {

    private static final String CODE_PREFIX = "code:";
    private static final String RATE_PREFIX = "code:rate:";
    private static final int CODE_LENGTH = 6;
    private static final int CODE_TTL_MINUTES = 5;
    private static final int RATE_LIMIT_SECONDS = 60;

    private final RedisTemplate<String, String> redisTemplate;
    private final SecureRandom random = new SecureRandom();

    public VerificationCodeService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 生成并存储验证码。
     *
     * @param email 接收邮箱
     * @param type  用途: login / register
     * @return 6位数字验证码
     * @throws RuntimeException 若 60 秒内重复请求
     */
    public String generateCode(String email, String type) {
        String rateKey = RATE_PREFIX + type + ":" + email;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(rateKey))) {
            throw new RuntimeException("验证码发送过于频繁，请 60 秒后再试");
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        String codeKey = CODE_PREFIX + type + ":" + email;

        redisTemplate.opsForValue().set(codeKey, code, CODE_TTL_MINUTES, TimeUnit.MINUTES);
        redisTemplate.opsForValue().set(rateKey, "1", RATE_LIMIT_SECONDS, TimeUnit.SECONDS);

        log.info("验证码已生成: email={}, type={}, code={}", email, type, code);
        return code;
    }

    /**
     * 校验验证码——无论是否匹配，使用后立即删除。
     *
     * @param email 接收邮箱
     * @param type  用途: login / register
     * @param code  用户输入的验证码
     * @return true 表示验证通过
     */
    public boolean verifyCode(String email, String type, String code) {
        String key = CODE_PREFIX + type + ":" + email;
        String stored = redisTemplate.opsForValue().get(key);
        if (stored == null) {
            log.debug("验证码不存在或已过期: email={}, type={}", email, type);
            return false;
        }
        boolean matched = stored.equals(code);
        if (matched) {
            redisTemplate.delete(key);
            log.info("验证码校验通过: email={}, type={}", email, type);
        } else {
            log.warn("验证码不匹配: email={}, type={}", email, type);
        }
        return matched;
    }
}
