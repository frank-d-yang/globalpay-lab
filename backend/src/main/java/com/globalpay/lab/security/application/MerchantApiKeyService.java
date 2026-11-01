package com.globalpay.lab.security.application;

import com.globalpay.lab.merchant.application.MerchantService;
import com.globalpay.lab.merchant.domain.Merchant;
import com.globalpay.lab.merchant.domain.MerchantStatus;
import com.globalpay.lab.security.domain.ApiKeyStatus;
import com.globalpay.lab.security.domain.MerchantApiKey;
import com.globalpay.lab.security.infrastructure.MerchantApiKeyMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class MerchantApiKeyService {

    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    private MerchantService merchantService;

    @Autowired
    private MerchantApiKeyMapper apiKeyMapper;

    /**
     * 我们的 issue key，返回的是keyId+secretkey，而不是MerchantId + secretkey.
     *
     * 引入keyId，是考虑到一个商户可能拥有多个API key
     * 而不是像我们平时的那种 username + password，一一对应
     *
     * Merchant ID 代表商户身份，API Key Secret 负责证明调用者有权使用这个身份，
     * 而 Key ID 只是帮助我们找到具体的那份凭证
     * @param merchantId
     * @return
     */
    public String issueKey(UUID merchantId) {

        Merchant merchant = merchantService.getMerchant(merchantId);

        if (merchant.getStatus() != MerchantStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Merchant is not active"
            );
        }

        // Generate a 256-bit random secret.
        byte[] secretBytes = new byte[32];
        secureRandom.nextBytes(secretBytes);

        String secret = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(secretBytes);

        UUID keyId = UUID.randomUUID();

        // Store only the hash of the secret.
        String keyHash = sha256Hex(secret);

        MerchantApiKey apiKey = new MerchantApiKey(
                keyId,
                merchantId,
                keyHash,
                ApiKeyStatus.ACTIVE,
                null,
                null
        );

        apiKeyMapper.insert(apiKey);

        // The raw key is returned only during issuance.
        return "gpl_test_" + keyId + "." + secret;
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    value.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable", ex
            );
        }
    }

    public UUID authenticate(String rawKey) {

        // 1. Validate the API key format
        if (rawKey == null || !rawKey.startsWith("gpl_test_")) {
            throw new BadCredentialsException("Invalid API key");
        }

        int separator = rawKey.indexOf('.');

        if (separator < 0) {
            throw new BadCredentialsException("Invalid API key");
        }

        // 2. Extract Key ID and Secret
        UUID keyId;

        try {
            String id = rawKey.substring(
                    "gpl_test_".length(), separator
            );
            keyId = UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            throw new BadCredentialsException("Invalid API key");
        }

        String secret = rawKey.substring(separator + 1);

        if (!secret.matches("[A-Za-z0-9_-]{43}")) {
            throw new BadCredentialsException("Invalid API key");
        }

        // 3. Find the API key record
        MerchantApiKey apiKey = apiKeyMapper.findById(keyId);

        if (apiKey == null ||
                apiKey.getStatus() != ApiKeyStatus.ACTIVE) {
            throw new BadCredentialsException("Invalid API key");
        }

        // 4. Compare SHA-256 hashes
        String incomingHash = sha256Hex(secret);

        boolean matches = MessageDigest.isEqual(
                HexFormat.of().parseHex(apiKey.getKeyHash()),
                HexFormat.of().parseHex(incomingHash)
        );

        if (!matches) {
            throw new BadCredentialsException("Invalid API key");
        }

        // 5. Verify the owning merchant
        Merchant merchant =
                merchantService.getMerchant(apiKey.getMerchantId());

        if (merchant.getStatus() != MerchantStatus.ACTIVE) {
            throw new BadCredentialsException("Invalid API key");
        }

        return merchant.getId();
    }

    public boolean revokeKey(UUID keyId) {

        if (keyId == null) {
            throw new IllegalArgumentException(
                    "API key ID must not be null"
            );
        }

        return apiKeyMapper.revoke(keyId) == 1;
    }
}

