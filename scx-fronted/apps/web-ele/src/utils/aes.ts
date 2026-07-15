import CryptoJS from 'crypto-js';

function deriveKey(keyStr: string) {
  // Must match AesUtils.deriveKey on the Java service.
  return CryptoJS.SHA256(keyStr);
}

export const encrypt = (word: string, keyStr: string) => {
  const srcs = CryptoJS.enc.Utf8.parse(word);
  const encrypted = CryptoJS.AES.encrypt(srcs, deriveKey(keyStr), {
    mode: CryptoJS.mode.ECB,
    padding: CryptoJS.pad.Pkcs7,
  });
  return encrypted.toString();
};
