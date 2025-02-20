package org.signal.libsignal.protocol;

import junit.framework.TestCase;

import org.signal.libsignal.protocol.ecc.Curve;
import org.signal.libsignal.protocol.ecc.ECKeyPair;
import org.signal.libsignal.protocol.kem.KEMKeyPair;
import org.signal.libsignal.protocol.kem.KEMKeyType;
import org.signal.libsignal.protocol.message.CiphertextMessage;
import org.signal.libsignal.protocol.message.PreKeySignalMessage;
import org.signal.libsignal.protocol.message.SignalMessage;
import org.signal.libsignal.protocol.state.KyberPreKeyRecord;
import org.signal.libsignal.protocol.state.PreKeyBundle;
import org.signal.libsignal.protocol.state.PreKeyRecord;
import org.signal.libsignal.protocol.state.SignalProtocolStore;
import org.signal.libsignal.protocol.state.SignedPreKeyRecord;
import org.signal.libsignal.protocol.util.Medium;

import java.util.Arrays;
import java.util.Random;

public class SimultaneousInitiateTests extends TestCase {

  private static final SignalProtocolAddress BOB_ADDRESS   = new SignalProtocolAddress("+14151231234", 1);
  private static final SignalProtocolAddress ALICE_ADDRESS = new SignalProtocolAddress("+14159998888", 1);

  private static final ECKeyPair aliceSignedPreKey = Curve.generateKeyPair();
  private static final KEMKeyPair aliceKyberPreKey = KEMKeyPair.generate(KEMKeyType.KYBER_1024);
  private static final ECKeyPair bobSignedPreKey   = Curve.generateKeyPair();
  private static final KEMKeyPair bobKyberPreKey   = KEMKeyPair.generate(KEMKeyType.KYBER_1024);

  private static final int aliceSignedPreKeyId = new Random().nextInt(Medium.MAX_VALUE);
  private static final int aliceKyberPreKeyId  = new Random().nextInt(Medium.MAX_VALUE);
  private static final int bobSignedPreKeyId   = new Random().nextInt(Medium.MAX_VALUE);
  private static final int bobKyberPreKeyId    = new Random().nextInt(Medium.MAX_VALUE);

  public void testBasicSimultaneousInitiate()
      throws InvalidKeyException, UntrustedIdentityException, InvalidVersionException,
      InvalidMessageException, DuplicateMessageException, LegacyMessageException,
      InvalidKeyIdException, NoSessionException
  {
    SignalProtocolStore aliceStore = new TestInMemorySignalProtocolStore();
    SignalProtocolStore bobStore   = new TestInMemorySignalProtocolStore();

    PreKeyBundle alicePreKeyBundle = createAlicePreKeyBundle(aliceStore);
    PreKeyBundle bobPreKeyBundle = createBobPreKeyBundle(bobStore);

    SessionBuilder aliceSessionBuilder = new SessionBuilder(aliceStore, BOB_ADDRESS);
    SessionBuilder bobSessionBuilder   = new SessionBuilder(bobStore, ALICE_ADDRESS);

    SessionCipher aliceSessionCipher = new SessionCipher(aliceStore, BOB_ADDRESS);
    SessionCipher bobSessionCipher = new SessionCipher(bobStore, ALICE_ADDRESS);

    aliceSessionBuilder.process(bobPreKeyBundle);
    bobSessionBuilder.process(alicePreKeyBundle);

    CiphertextMessage messageForBob   = aliceSessionCipher.encrypt("hey there".getBytes());
    CiphertextMessage messageForAlice = bobSessionCipher.encrypt("sample message".getBytes());

    assertTrue(messageForBob.getType() == CiphertextMessage.PREKEY_TYPE);
    assertTrue(messageForAlice.getType() == CiphertextMessage.PREKEY_TYPE);

    assertFalse(isSessionIdEqual(aliceStore, bobStore));

    byte[] alicePlaintext = aliceSessionCipher.decrypt(new PreKeySignalMessage(messageForAlice.serialize()));
    byte[] bobPlaintext   = bobSessionCipher.decrypt(new PreKeySignalMessage(messageForBob.serialize()));

    assertTrue(new String(alicePlaintext).equals("sample message"));
    assertTrue(new String(bobPlaintext).equals("hey there"));

    assertTrue(aliceStore.loadSession(BOB_ADDRESS).getSessionVersion() == 4);
    assertTrue(bobStore.loadSession(ALICE_ADDRESS).getSessionVersion() == 4);

    assertFalse(isSessionIdEqual(aliceStore, bobStore));

    CiphertextMessage aliceResponse = aliceSessionCipher.encrypt("second message".getBytes());

    assertTrue(aliceResponse.getType() == CiphertextMessage.WHISPER_TYPE);

    byte[] responsePlaintext = bobSessionCipher.decrypt(new SignalMessage(aliceResponse.serialize()));

    assertTrue(new String(responsePlaintext).equals("second message"));
    assertTrue(isSessionIdEqual(aliceStore, bobStore));

    CiphertextMessage finalMessage = bobSessionCipher.encrypt("third message".getBytes());

    assertTrue(finalMessage.getType() == CiphertextMessage.WHISPER_TYPE);

    byte[] finalPlaintext = aliceSessionCipher.decrypt(new SignalMessage(finalMessage.serialize()));

    assertTrue(new String(finalPlaintext).equals("third message"));
    assertTrue(isSessionIdEqual(aliceStore, bobStore));
  }

  public void testLostSimultaneousInitiate() throws InvalidKeyException, UntrustedIdentityException, InvalidVersionException, InvalidMessageException, DuplicateMessageException, LegacyMessageException, InvalidKeyIdException, NoSessionException {
    SignalProtocolStore aliceStore = new TestInMemorySignalProtocolStore();
    SignalProtocolStore bobStore   = new TestInMemorySignalProtocolStore();

    PreKeyBundle alicePreKeyBundle = createAlicePreKeyBundle(aliceStore);
    PreKeyBundle bobPreKeyBundle = createBobPreKeyBundle(bobStore);

    SessionBuilder aliceSessionBuilder = new SessionBuilder(aliceStore, BOB_ADDRESS);
    SessionBuilder bobSessionBuilder   = new SessionBuilder(bobStore, ALICE_ADDRESS);

    SessionCipher aliceSessionCipher = new SessionCipher(aliceStore, BOB_ADDRESS);
    SessionCipher bobSessionCipher = new SessionCipher(bobStore, ALICE_ADDRESS);

    aliceSessionBuilder.process(bobPreKeyBundle);
    bobSessionBuilder.process(alicePreKeyBundle);

    CiphertextMessage messageForBob   = aliceSessionCipher.encrypt("hey there".getBytes());
    CiphertextMessage messageForAlice = bobSessionCipher.encrypt("sample message".getBytes());

    assertTrue(messageForBob.getType() == CiphertextMessage.PREKEY_TYPE);
    assertTrue(messageForAlice.getType() == CiphertextMessage.PREKEY_TYPE);

    assertFalse(isSessionIdEqual(aliceStore, bobStore));

    byte[] bobPlaintext   = bobSessionCipher.decrypt(new PreKeySignalMessage(messageForBob.serialize()));

    assertTrue(new String(bobPlaintext).equals("hey there"));
    assertTrue(bobStore.loadSession(ALICE_ADDRESS).getSessionVersion() == 4);

    CiphertextMessage aliceResponse = aliceSessionCipher.encrypt("second message".getBytes());

    assertTrue(aliceResponse.getType() == CiphertextMessage.PREKEY_TYPE);

    byte[] responsePlaintext = bobSessionCipher.decrypt(new PreKeySignalMessage(aliceResponse.serialize()));

    assertTrue(new String(responsePlaintext).equals("second message"));
    assertTrue(isSessionIdEqual(aliceStore, bobStore));

    CiphertextMessage finalMessage = bobSessionCipher.encrypt("third message".getBytes());

    assertTrue(finalMessage.getType() == CiphertextMessage.WHISPER_TYPE);

    byte[] finalPlaintext = aliceSessionCipher.decrypt(new SignalMessage(finalMessage.serialize()));

    assertTrue(new String(finalPlaintext).equals("third message"));
    assertTrue(isSessionIdEqual(aliceStore, bobStore));
  }

  public void testSimultaneousInitiateLostMessage()
      throws InvalidKeyException, UntrustedIdentityException, InvalidVersionException,
      InvalidMessageException, DuplicateMessageException, LegacyMessageException,
      InvalidKeyIdException, NoSessionException
  {
    SignalProtocolStore aliceStore = new TestInMemorySignalProtocolStore();
    SignalProtocolStore bobStore   = new TestInMemorySignalProtocolStore();

    PreKeyBundle alicePreKeyBundle = createAlicePreKeyBundle(aliceStore);
    PreKeyBundle bobPreKeyBundle = createBobPreKeyBundle(bobStore);

    SessionBuilder aliceSessionBuilder = new SessionBuilder(aliceStore, BOB_ADDRESS);
    SessionBuilder bobSessionBuilder   = new SessionBuilder(bobStore, ALICE_ADDRESS);

    SessionCipher aliceSessionCipher = new SessionCipher(aliceStore, BOB_ADDRESS);
    SessionCipher bobSessionCipher = new SessionCipher(bobStore, ALICE_ADDRESS);

    aliceSessionBuilder.process(bobPreKeyBundle);
    bobSessionBuilder.process(alicePreKeyBundle);

    CiphertextMessage messageForBob   = aliceSessionCipher.encrypt("hey there".getBytes());
    CiphertextMessage messageForAlice = bobSessionCipher.encrypt("sample message".getBytes());

    assertTrue(messageForBob.getType() == CiphertextMessage.PREKEY_TYPE);
    assertTrue(messageForAlice.getType() == CiphertextMessage.PREKEY_TYPE);

    assertFalse(isSessionIdEqual(aliceStore, bobStore));

    byte[] alicePlaintext = aliceSessionCipher.decrypt(new PreKeySignalMessage(messageForAlice.serialize()));
    byte[] bobPlaintext   = bobSessionCipher.decrypt(new PreKeySignalMessage(messageForBob.serialize()));

    assertTrue(new String(alicePlaintext).equals("sample message"));
    assertTrue(new String(bobPlaintext).equals("hey there"));

    assertTrue(aliceStore.loadSession(BOB_ADDRESS).getSessionVersion() == 4);
    assertTrue(bobStore.loadSession(ALICE_ADDRESS).getSessionVersion() == 4);

    assertFalse(isSessionIdEqual(aliceStore, bobStore));

    CiphertextMessage aliceResponse = aliceSessionCipher.encrypt("second message".getBytes());

    assertTrue(aliceResponse.getType() == CiphertextMessage.WHISPER_TYPE);

//    byte[] responsePlaintext = bobSessionCipher.decrypt(new WhisperMessage(aliceResponse.serialize()));
//
//    assertTrue(new String(responsePlaintext).equals("second message"));
//    assertTrue(isSessionIdEqual(aliceStore, bobStore));
    assertFalse(isSessionIdEqual(aliceStore, bobStore));

    CiphertextMessage finalMessage = bobSessionCipher.encrypt("third message".getBytes());

    assertTrue(finalMessage.getType() == CiphertextMessage.WHISPER_TYPE);

    byte[] finalPlaintext = aliceSessionCipher.decrypt(new SignalMessage(finalMessage.serialize()));

    assertTrue(new String(finalPlaintext).equals("third message"));
    assertTrue(isSessionIdEqual(aliceStore, bobStore));
  }

  public void testSimultaneousInitiateRepeatedMessages()
      throws InvalidKeyException, UntrustedIdentityException, InvalidVersionException,
      InvalidMessageException, DuplicateMessageException, LegacyMessageException,
      InvalidKeyIdException, NoSessionException
  {
    SignalProtocolStore aliceStore = new TestInMemorySignalProtocolStore();
    SignalProtocolStore bobStore   = new TestInMemorySignalProtocolStore();

    PreKeyBundle alicePreKeyBundle = createAlicePreKeyBundle(aliceStore);
    PreKeyBundle bobPreKeyBundle = createBobPreKeyBundle(bobStore);

    SessionBuilder aliceSessionBuilder = new SessionBuilder(aliceStore, BOB_ADDRESS);
    SessionBuilder bobSessionBuilder   = new SessionBuilder(bobStore, ALICE_ADDRESS);

    SessionCipher aliceSessionCipher = new SessionCipher(aliceStore, BOB_ADDRESS);
    SessionCipher bobSessionCipher = new SessionCipher(bobStore, ALICE_ADDRESS);

    aliceSessionBuilder.process(bobPreKeyBundle);
    bobSessionBuilder.process(alicePreKeyBundle);

    CiphertextMessage messageForBob   = aliceSessionCipher.encrypt("hey there".getBytes());
    CiphertextMessage messageForAlice = bobSessionCipher.encrypt("sample message".getBytes());

    assertTrue(messageForBob.getType() == CiphertextMessage.PREKEY_TYPE);
    assertTrue(messageForAlice.getType() == CiphertextMessage.PREKEY_TYPE);

    assertFalse(isSessionIdEqual(aliceStore, bobStore));

    byte[] alicePlaintext = aliceSessionCipher.decrypt(new PreKeySignalMessage(messageForAlice.serialize()));
    byte[] bobPlaintext   = bobSessionCipher.decrypt(new PreKeySignalMessage(messageForBob.serialize()));

    assertTrue(new String(alicePlaintext).equals("sample message"));
    assertTrue(new String(bobPlaintext).equals("hey there"));

    assertTrue(aliceStore.loadSession(BOB_ADDRESS).getSessionVersion() == 4);
    assertTrue(bobStore.loadSession(ALICE_ADDRESS).getSessionVersion() == 4);

    assertFalse(isSessionIdEqual(aliceStore, bobStore));

    for (int i=0;i<50;i++) {
      CiphertextMessage messageForBobRepeat   = aliceSessionCipher.encrypt("hey there".getBytes());
      CiphertextMessage messageForAliceRepeat = bobSessionCipher.encrypt("sample message".getBytes());

      assertTrue(messageForBobRepeat.getType() == CiphertextMessage.WHISPER_TYPE);
      assertTrue(messageForAliceRepeat.getType() == CiphertextMessage.WHISPER_TYPE);

      assertFalse(isSessionIdEqual(aliceStore, bobStore));

      byte[] alicePlaintextRepeat = aliceSessionCipher.decrypt(new SignalMessage(messageForAliceRepeat.serialize()));
      byte[] bobPlaintextRepeat   = bobSessionCipher.decrypt(new SignalMessage(messageForBobRepeat.serialize()));

      assertTrue(new String(alicePlaintextRepeat).equals("sample message"));
      assertTrue(new String(bobPlaintextRepeat).equals("hey there"));

      assertFalse(isSessionIdEqual(aliceStore, bobStore));
    }

    CiphertextMessage aliceResponse = aliceSessionCipher.encrypt("second message".getBytes());

    assertTrue(aliceResponse.getType() == CiphertextMessage.WHISPER_TYPE);

    byte[] responsePlaintext = bobSessionCipher.decrypt(new SignalMessage(aliceResponse.serialize()));

    assertTrue(new String(responsePlaintext).equals("second message"));
    assertTrue(isSessionIdEqual(aliceStore, bobStore));

    CiphertextMessage finalMessage = bobSessionCipher.encrypt("third message".getBytes());

    assertTrue(finalMessage.getType() == CiphertextMessage.WHISPER_TYPE);

    byte[] finalPlaintext = aliceSessionCipher.decrypt(new SignalMessage(finalMessage.serialize()));

    assertTrue(new String(finalPlaintext).equals("third message"));
    assertTrue(isSessionIdEqual(aliceStore, bobStore));
  }

  public void testRepeatedSimultaneousInitiateRepeatedMessages()
      throws InvalidKeyException, UntrustedIdentityException, InvalidVersionException,
      InvalidMessageException, DuplicateMessageException, LegacyMessageException,
      InvalidKeyIdException, NoSessionException
  {
    SignalProtocolStore aliceStore = new TestInMemorySignalProtocolStore();
    SignalProtocolStore bobStore   = new TestInMemorySignalProtocolStore();


    SessionBuilder aliceSessionBuilder = new SessionBuilder(aliceStore, BOB_ADDRESS);
    SessionBuilder bobSessionBuilder   = new SessionBuilder(bobStore, ALICE_ADDRESS);

    SessionCipher aliceSessionCipher = new SessionCipher(aliceStore, BOB_ADDRESS);
    SessionCipher bobSessionCipher = new SessionCipher(bobStore, ALICE_ADDRESS);

    for (int i=0;i<15;i++) {
      PreKeyBundle alicePreKeyBundle = createAlicePreKeyBundle(aliceStore);
      PreKeyBundle bobPreKeyBundle   = createBobPreKeyBundle(bobStore);

      aliceSessionBuilder.process(bobPreKeyBundle);
      bobSessionBuilder.process(alicePreKeyBundle);

      CiphertextMessage messageForBob = aliceSessionCipher.encrypt("hey there".getBytes());
      CiphertextMessage messageForAlice = bobSessionCipher.encrypt("sample message".getBytes());

      assertTrue(messageForBob.getType() == CiphertextMessage.PREKEY_TYPE);
      assertTrue(messageForAlice.getType() == CiphertextMessage.PREKEY_TYPE);

      assertFalse(isSessionIdEqual(aliceStore, bobStore));

      byte[] alicePlaintext = aliceSessionCipher.decrypt(new PreKeySignalMessage(messageForAlice.serialize()));
      byte[] bobPlaintext = bobSessionCipher.decrypt(new PreKeySignalMessage(messageForBob.serialize()));

      assertTrue(new String(alicePlaintext).equals("sample message"));
      assertTrue(new String(bobPlaintext).equals("hey there"));

      assertTrue(aliceStore.loadSession(BOB_ADDRESS).getSessionVersion() == 4);
      assertTrue(bobStore.loadSession(ALICE_ADDRESS).getSessionVersion() == 4);

      assertFalse(isSessionIdEqual(aliceStore, bobStore));
    }

    for (int i=0;i<50;i++) {
      CiphertextMessage messageForBobRepeat   = aliceSessionCipher.encrypt("hey there".getBytes());
      CiphertextMessage messageForAliceRepeat = bobSessionCipher.encrypt("sample message".getBytes());

      assertTrue(messageForBobRepeat.getType() == CiphertextMessage.WHISPER_TYPE);
      assertTrue(messageForAliceRepeat.getType() == CiphertextMessage.WHISPER_TYPE);

      assertFalse(isSessionIdEqual(aliceStore, bobStore));

      byte[] alicePlaintextRepeat = aliceSessionCipher.decrypt(new SignalMessage(messageForAliceRepeat.serialize()));
      byte[] bobPlaintextRepeat   = bobSessionCipher.decrypt(new SignalMessage(messageForBobRepeat.serialize()));

      assertTrue(new String(alicePlaintextRepeat).equals("sample message"));
      assertTrue(new String(bobPlaintextRepeat).equals("hey there"));

      assertFalse(isSessionIdEqual(aliceStore, bobStore));
    }

    CiphertextMessage aliceResponse = aliceSessionCipher.encrypt("second message".getBytes());

    assertTrue(aliceResponse.getType() == CiphertextMessage.WHISPER_TYPE);

    byte[] responsePlaintext = bobSessionCipher.decrypt(new SignalMessage(aliceResponse.serialize()));

    assertTrue(new String(responsePlaintext).equals("second message"));
    assertTrue(isSessionIdEqual(aliceStore, bobStore));

    CiphertextMessage finalMessage = bobSessionCipher.encrypt("third message".getBytes());

    assertTrue(finalMessage.getType() == CiphertextMessage.WHISPER_TYPE);

    byte[] finalPlaintext = aliceSessionCipher.decrypt(new SignalMessage(finalMessage.serialize()));

    assertTrue(new String(finalPlaintext).equals("third message"));
    assertTrue(isSessionIdEqual(aliceStore, bobStore));
  }

  public void testRepeatedSimultaneousInitiateLostMessageRepeatedMessages()
      throws InvalidKeyException, UntrustedIdentityException, InvalidVersionException,
      InvalidMessageException, DuplicateMessageException, LegacyMessageException,
      InvalidKeyIdException, NoSessionException
  {
    SignalProtocolStore aliceStore = new TestInMemorySignalProtocolStore();
    SignalProtocolStore bobStore   = new TestInMemorySignalProtocolStore();


    SessionBuilder aliceSessionBuilder = new SessionBuilder(aliceStore, BOB_ADDRESS);
    SessionBuilder bobSessionBuilder   = new SessionBuilder(bobStore, ALICE_ADDRESS);

    SessionCipher aliceSessionCipher = new SessionCipher(aliceStore, BOB_ADDRESS);
    SessionCipher bobSessionCipher = new SessionCipher(bobStore, ALICE_ADDRESS);

//    PreKeyBundle aliceLostPreKeyBundle = createAlicePreKeyBundle(aliceStore);
    PreKeyBundle bobLostPreKeyBundle   = createBobPreKeyBundle(bobStore);

    aliceSessionBuilder.process(bobLostPreKeyBundle);
//    bobSessionBuilder.process(aliceLostPreKeyBundle);

    CiphertextMessage lostMessageForBob   = aliceSessionCipher.encrypt("hey there".getBytes());
//    CiphertextMessage lostMessageForAlice = bobSessionCipher.encrypt("sample message".getBytes());

    for (int i=0;i<15;i++) {
      PreKeyBundle alicePreKeyBundle = createAlicePreKeyBundle(aliceStore);
      PreKeyBundle bobPreKeyBundle   = createBobPreKeyBundle(bobStore);

      aliceSessionBuilder.process(bobPreKeyBundle);
      bobSessionBuilder.process(alicePreKeyBundle);

      CiphertextMessage messageForBob = aliceSessionCipher.encrypt("hey there".getBytes());
      CiphertextMessage messageForAlice = bobSessionCipher.encrypt("sample message".getBytes());

      assertTrue(messageForBob.getType() == CiphertextMessage.PREKEY_TYPE);
      assertTrue(messageForAlice.getType() == CiphertextMessage.PREKEY_TYPE);

      assertFalse(isSessionIdEqual(aliceStore, bobStore));

      byte[] alicePlaintext = aliceSessionCipher.decrypt(new PreKeySignalMessage(messageForAlice.serialize()));
      byte[] bobPlaintext = bobSessionCipher.decrypt(new PreKeySignalMessage(messageForBob.serialize()));

      assertTrue(new String(alicePlaintext).equals("sample message"));
      assertTrue(new String(bobPlaintext).equals("hey there"));

      assertTrue(aliceStore.loadSession(BOB_ADDRESS).getSessionVersion() == 4);
      assertTrue(bobStore.loadSession(ALICE_ADDRESS).getSessionVersion() == 4);

      assertFalse(isSessionIdEqual(aliceStore, bobStore));
    }

    for (int i=0;i<50;i++) {
      CiphertextMessage messageForBobRepeat   = aliceSessionCipher.encrypt("hey there".getBytes());
      CiphertextMessage messageForAliceRepeat = bobSessionCipher.encrypt("sample message".getBytes());

      assertTrue(messageForBobRepeat.getType() == CiphertextMessage.WHISPER_TYPE);
      assertTrue(messageForAliceRepeat.getType() == CiphertextMessage.WHISPER_TYPE);

      assertFalse(isSessionIdEqual(aliceStore, bobStore));

      byte[] alicePlaintextRepeat = aliceSessionCipher.decrypt(new SignalMessage(messageForAliceRepeat.serialize()));
      byte[] bobPlaintextRepeat   = bobSessionCipher.decrypt(new SignalMessage(messageForBobRepeat.serialize()));

      assertTrue(new String(alicePlaintextRepeat).equals("sample message"));
      assertTrue(new String(bobPlaintextRepeat).equals("hey there"));

      assertFalse(isSessionIdEqual(aliceStore, bobStore));
    }

    CiphertextMessage aliceResponse = aliceSessionCipher.encrypt("second message".getBytes());

    assertTrue(aliceResponse.getType() == CiphertextMessage.WHISPER_TYPE);

    byte[] responsePlaintext = bobSessionCipher.decrypt(new SignalMessage(aliceResponse.serialize()));

    assertTrue(new String(responsePlaintext).equals("second message"));
    assertTrue(isSessionIdEqual(aliceStore, bobStore));

    CiphertextMessage finalMessage = bobSessionCipher.encrypt("third message".getBytes());

    assertTrue(finalMessage.getType() == CiphertextMessage.WHISPER_TYPE);

    byte[] finalPlaintext = aliceSessionCipher.decrypt(new SignalMessage(finalMessage.serialize()));

    assertTrue(new String(finalPlaintext).equals("third message"));
    assertTrue(isSessionIdEqual(aliceStore, bobStore));

    byte[] lostMessagePlaintext = bobSessionCipher.decrypt(new PreKeySignalMessage(lostMessageForBob.serialize()));
    assertTrue(new String(lostMessagePlaintext).equals("hey there"));

    assertFalse(isSessionIdEqual(aliceStore, bobStore));

    CiphertextMessage blastFromThePast          = bobSessionCipher.encrypt("unexpected!".getBytes());
    byte[]            blastFromThePastPlaintext = aliceSessionCipher.decrypt(new SignalMessage(blastFromThePast.serialize()));

    assertTrue(new String(blastFromThePastPlaintext).equals("unexpected!"));
    assertTrue(isSessionIdEqual(aliceStore, bobStore));
  }

  private boolean isSessionIdEqual(SignalProtocolStore aliceStore, SignalProtocolStore bobStore) {
    return Arrays.equals(aliceStore.loadSession(BOB_ADDRESS).getAliceBaseKey(),
                         bobStore.loadSession(ALICE_ADDRESS).getAliceBaseKey());
  }

  private PreKeyBundle createAlicePreKeyBundle(SignalProtocolStore aliceStore) throws InvalidKeyException {
    ECKeyPair aliceUnsignedPreKey   = Curve.generateKeyPair();
    int       aliceUnsignedPreKeyId = new Random().nextInt(Medium.MAX_VALUE);
    byte[]    aliceSignature        = Curve.calculateSignature(aliceStore.getIdentityKeyPair().getPrivateKey(),
                                                               aliceSignedPreKey.getPublicKey().serialize());
    byte[]    aliceKyberSignature   = Curve.calculateSignature(aliceStore.getIdentityKeyPair().getPrivateKey(),
                                                               aliceKyberPreKey.getPublicKey().serialize());

    PreKeyBundle alicePreKeyBundle = new PreKeyBundle(1, 1,
                                                      aliceUnsignedPreKeyId, aliceUnsignedPreKey.getPublicKey(),
                                                      aliceSignedPreKeyId, aliceSignedPreKey.getPublicKey(),
                                                      aliceSignature, aliceStore.getIdentityKeyPair().getPublicKey(),
                                                      aliceKyberPreKeyId, aliceKyberPreKey.getPublicKey(),
                                                      aliceKyberSignature);

    aliceStore.storeSignedPreKey(aliceSignedPreKeyId, new SignedPreKeyRecord(aliceSignedPreKeyId, System.currentTimeMillis(), aliceSignedPreKey, aliceSignature));
    aliceStore.storeKyberPreKey(aliceKyberPreKeyId, new KyberPreKeyRecord(aliceKyberPreKeyId, System.currentTimeMillis(), aliceKyberPreKey, aliceKyberSignature));
    aliceStore.storePreKey(aliceUnsignedPreKeyId, new PreKeyRecord(aliceUnsignedPreKeyId, aliceUnsignedPreKey));

    return alicePreKeyBundle;
  }

  private PreKeyBundle createBobPreKeyBundle(SignalProtocolStore bobStore) throws InvalidKeyException {
    ECKeyPair bobUnsignedPreKey   = Curve.generateKeyPair();
    int       bobUnsignedPreKeyId = new Random().nextInt(Medium.MAX_VALUE);
    byte[]    bobSignature        = Curve.calculateSignature(bobStore.getIdentityKeyPair().getPrivateKey(),
                                                             bobSignedPreKey.getPublicKey().serialize());
    byte[]    bobKyberSignature   = Curve.calculateSignature(bobStore.getIdentityKeyPair().getPrivateKey(),
                                                             bobKyberPreKey.getPublicKey().serialize());

    PreKeyBundle bobPreKeyBundle = new PreKeyBundle(1, 1,
                                                    bobUnsignedPreKeyId, bobUnsignedPreKey.getPublicKey(),
                                                    bobSignedPreKeyId, bobSignedPreKey.getPublicKey(),
                                                    bobSignature, bobStore.getIdentityKeyPair().getPublicKey(),
                                                    bobKyberPreKeyId, bobKyberPreKey.getPublicKey(),
                                                    bobKyberSignature);

    bobStore.storeSignedPreKey(bobSignedPreKeyId, new SignedPreKeyRecord(bobSignedPreKeyId, System.currentTimeMillis(), bobSignedPreKey, bobSignature));
    bobStore.storeKyberPreKey(bobKyberPreKeyId, new KyberPreKeyRecord(bobKyberPreKeyId, System.currentTimeMillis(), bobKyberPreKey, bobKyberSignature));
    bobStore.storePreKey(bobUnsignedPreKeyId, new PreKeyRecord(bobUnsignedPreKeyId, bobUnsignedPreKey));

    return bobPreKeyBundle;
  }
}
