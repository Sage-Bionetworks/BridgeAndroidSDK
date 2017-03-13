package org.sagebionetworks.bridge.android.data;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.cms.CMSAlgorithm;
import org.spongycastle.cms.CMSEnvelopedDataStreamGenerator;
import org.spongycastle.cms.CMSException;
import org.spongycastle.cms.jcajce.JceCMSContentEncryptorBuilder;
import org.spongycastle.cms.jcajce.JceKeyTransRecipientInfoGenerator;
import org.spongycastle.jce.provider.BouncyCastleProvider;
import org.spongycastle.operator.OutputEncryptor;

import java.io.IOException;
import java.io.OutputStream;
import java.security.Security;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;

import static com.google.common.base.Preconditions.checkState;

/**
 * Encrypts data using a Study's public key, so Bridge can decrypt it after upload.
 */
public class StudyUploadEncryptor {
    private static final Logger LOG = LoggerFactory.getLogger(StudyUploadEncryptor.class);

    private static final String JCE_PROVIDER = "SC"; // SpongyCastle

    private final Supplier<JceKeyTransRecipientInfoGenerator> recipientInfoGeneratorSupplier;

    static {
        Security.insertProviderAt(new BouncyCastleProvider(), 1);
    }

    public StudyUploadEncryptor(X509Certificate publicKey) {
        this.recipientInfoGeneratorSupplier = Suppliers.memoize(() -> {
            try {
                return new JceKeyTransRecipientInfoGenerator(publicKey).setProvider(JCE_PROVIDER);
            } catch (CertificateEncodingException e) {
                LOG.error("Unable to create recipient archiveInfo generator from public key", e);
            }
            return null;
        });
    }

    /**
     * @param stream plaintext stream
     * @return encrypted stream
     * @throws CMSException problem with encryption
     * @throws IOException  problem with stream
     */
    public OutputStream encrypt(OutputStream stream) throws CMSException, IOException {
        JceKeyTransRecipientInfoGenerator recipientInfoGenerator = recipientInfoGeneratorSupplier
                .get();
        checkState(recipientInfoGenerator != null, "Recipient archiveInfo generator was not initialized");

        CMSEnvelopedDataStreamGenerator gen = new CMSEnvelopedDataStreamGenerator();
        gen.addRecipientInfoGenerator(recipientInfoGenerator);

        // Generate encrypted input stream in AES-256-CBC format, output is DER, not S/MIME or PEM
        OutputEncryptor encryptor =
                new JceCMSContentEncryptorBuilder(CMSAlgorithm.AES256_CBC).setProvider(JCE_PROVIDER)
                        .build();

        return gen.open(stream, encryptor);
    }
}
