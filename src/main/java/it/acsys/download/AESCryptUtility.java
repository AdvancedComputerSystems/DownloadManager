package it.acsys.download;

import java.io.UnsupportedEncodingException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.util.encoders.Base64;

public class AESCryptUtility {
    private final String MODE_PADDING = "/CBC/PKCS5Padding";
    private final int DEFAULT_SALT_SIZE_BYTES = 32;

    private final SecureRandom rand;

    private final String passwd = "8g5qT74KdUY";

    public AESCryptUtility() throws Exception {
        rand = SecureRandom.getInstance("SHA1PRNG");
    }

    private byte[] generateSalt(int size) {
        byte[] salt = new byte[size];
        rand.nextBytes(salt);

        return salt;
    }
    
    private Key generateKey(String password) throws NoSuchAlgorithmException, UnsupportedEncodingException {
    	  MessageDigest digester = MessageDigest.getInstance("SHA-256");
    	  digester.update(password.getBytes("UTF-8"));
    	  byte[] key = digester.digest();
    	  SecretKeySpec spec = new SecretKeySpec(key, "AES");
    	  return spec;
    	}

    private byte[] generateIV(Cipher cipher) {
        byte[] iv = new byte[cipher.getBlockSize()];
        rand.nextBytes(iv);

        return iv;
    }

    private byte[] appendArrays(byte[] firstArray, byte[] secondArray) {
        final byte[] result = new byte[firstArray.length + secondArray.length];

        System.arraycopy(firstArray, 0, result, 0, firstArray.length);
        System.arraycopy(secondArray, 0, result, firstArray.length,
                secondArray.length);

        return result;
    }

    public byte[] encrypt(String algorithm, int keySize, final byte[] message)
            throws Exception {
        Cipher cipher = Cipher.getInstance(algorithm + MODE_PADDING);

        // The salt size for the chosen algorithm is set to be equal
        // to the algorithm's block size (if it is a block algorithm).
        int saltSizeBytes = DEFAULT_SALT_SIZE_BYTES;
        int algorithmBlockSize = cipher.getBlockSize();
        if (algorithmBlockSize > 0) {
            saltSizeBytes = algorithmBlockSize;
        }

        // Create salt
        final byte[] salt = generateSalt(saltSizeBytes);

        //SecretKey key = generateKey(algorithm, keySize, salt);
        
        Key key = generateKey(passwd);

        // create a new IV for each encryption
        final IvParameterSpec ivParamSpec = new IvParameterSpec(
                generateIV(cipher));

        // Perform encryption using the Cipher
        cipher.init(Cipher.ENCRYPT_MODE, key, ivParamSpec);
        byte[] encryptedMessage = cipher.doFinal(message);

        // append the IV and salt
        encryptedMessage = appendArrays(ivParamSpec.getIV(), encryptedMessage);
        encryptedMessage = appendArrays(salt, encryptedMessage);

        return encryptedMessage;
    }

    public byte[] decrypt(String algorithm, int keySize,
            final byte[] encryptedMessage) throws Exception {
        Cipher cipher = Cipher.getInstance(algorithm + MODE_PADDING);

        // determine the salt size for the first layer of encryption
        int saltSizeBytes = DEFAULT_SALT_SIZE_BYTES;
        int algorithmBlockSize = cipher.getBlockSize();
        if (algorithmBlockSize > 0) {
            saltSizeBytes = algorithmBlockSize;
        }

        byte[] decryptedMessage = new byte[encryptedMessage.length];
        System.arraycopy(encryptedMessage, 0, decryptedMessage, 0,
                encryptedMessage.length);

        // extract the salt and IV from the incoming message
        byte[] salt = null;
        byte[] iv = null;
        byte[] encryptedMessageKernel = null;
        final int saltStart = 0;
        final int saltSize = (saltSizeBytes < decryptedMessage.length ? saltSizeBytes
                : decryptedMessage.length);
        // final int saltSize = 32;
        // System.out.println("saltSize:"+saltSize);

        final int ivStart = (saltSizeBytes < decryptedMessage.length ? saltSizeBytes
                : decryptedMessage.length);
        final int ivSize = cipher.getBlockSize();
        final int encMesKernelStart = (saltSizeBytes + ivSize < decryptedMessage.length ? saltSizeBytes
                + ivSize
                : decryptedMessage.length);
        final int encMesKernelSize = (saltSizeBytes + ivSize < decryptedMessage.length ? (decryptedMessage.length
                - saltSizeBytes - ivSize)
                : 0);

        salt = new byte[saltSize];
        iv = new byte[ivSize];
        

        encryptedMessageKernel = new byte[encMesKernelSize];

        System.arraycopy(decryptedMessage, saltStart, salt, 0, saltSize);
        System.arraycopy(decryptedMessage, ivStart, iv, 0, ivSize);
        System.arraycopy(decryptedMessage, encMesKernelStart,
                encryptedMessageKernel, 0, encMesKernelSize);

        //SecretKey key = generateKey(algorithm, keySize, salt);
        Key key = generateKey(passwd);

        IvParameterSpec ivParamSpec = new IvParameterSpec(iv);

        // Perform decryption using the Cipher
        cipher.init(Cipher.DECRYPT_MODE, key, ivParamSpec);
        decryptedMessage = cipher.doFinal(encryptedMessageKernel);

        // Return the results
        return decryptedMessage;
    }

    public String decryptString(String toDecrypt) throws Exception {
        byte[] decriptata = decrypt("AES", 256,  Base64.decode(toDecrypt));
        return new String(decriptata);
    }
    
    public String encryptString(String toEncrypt) throws Exception {
       String toDecrypt = new String(Base64.encode(encrypt("AES", 256, toEncrypt.getBytes())));
       return toDecrypt;
    }

}
