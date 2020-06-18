package com.hedera.services.legacy.unit.serialization;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import static com.hedera.services.legacy.unit.serialization.FCMapSerializationTest.serialize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import com.hedera.services.state.merkle.BlobPath;
import com.hedera.services.state.merkle.OptionalBlob;
import com.swirlds.blob.BinaryObjectStore;
import com.swirlds.common.io.SerializableDataInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import org.junit.FixMethodOrder;
import org.junit.jupiter.api.*;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

/**
 * @author plynn
 */
@RunWith(JUnitPlatform.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@DisplayName("StorageSerializer Test Suite")
public class StorageSerializerTest {
  private static String TEST_STRING1 = "Some string";
  private static String TEST_STRING2 = "Another string";
  private static byte[] TEST_BYTES1 = TEST_STRING1.getBytes();
  private static byte[] TEST_BYTES2 = TEST_STRING2.getBytes();
  private static long TEST_EXPIRATION = 4114602824L;

  @BeforeAll
  public void setUp() {

  }


  @Test
  public void aa_serialize_deserialize_key() {
    BlobPath sKey = new BlobPath(TEST_STRING1);

    byte[] serial_skey = null;
    try {
      serial_skey = serialize(sKey);
    } catch (Exception ex) {
      System.out.println("Serialization Failed " + ex.getMessage());
      fail();
    }
    assertNotNull(serial_skey);
     // Now take the bytearray and build it back

    ByteArrayInputStream in = null;
    SerializableDataInputStream dis = null;
    BlobPath sKeyReborn = new BlobPath();
    try {
      in = new ByteArrayInputStream(serial_skey);
      dis = new SerializableDataInputStream(in);

      sKeyReborn = BlobPath.deserialize(dis);
      //Write Assertions Here
      assertNotNull(sKeyReborn);
      assertEquals(sKey.getPath(), sKeyReborn.getPath());
      assertEquals(sKey, sKeyReborn);

      dis.close();
      in.close();

    } catch (Exception ex) {
      System.out.println("**EXCEPTION**" + ex.getMessage());
      fail();
    } finally {
      dis = null;
      in = null;
    }
  }

  @Test
  public void ab_key_equals() {
    BlobPath sKey1 = new BlobPath(TEST_STRING1);
    BlobPath sKey2 = new BlobPath(TEST_STRING1);
    BlobPath sKey3 = new BlobPath(TEST_STRING2);

    assertEquals(sKey1, sKey2);
    Assertions.assertFalse(sKey1.equals(sKey3));
  }

  @Test
  public void ba_serialize_deserialize_value() {
    OptionalBlob sVal = new OptionalBlob(TEST_BYTES1);

    byte[] serial_sval = null;
    try {
      serial_sval = serialize(sVal);
    } catch (Exception ex) {
      System.out.println("Serialization Failed " + ex.getMessage());
      fail();
    }
    assertNotNull(serial_sval);
    // Now take the bytearray and build it back

    ByteArrayInputStream in;
    DataInputStream dis;
    OptionalBlob sValReborn;
    try {
      in = new ByteArrayInputStream(serial_sval);
      dis = new SerializableDataInputStream(in);
      BinaryObjectStore.getInstance().startInit();
      sValReborn = OptionalBlob.deserialize(dis);
      BinaryObjectStore.getInstance().stopInit();
      //Write Assertions Here
      assertNotNull(sValReborn);
      assertEquals(sVal, sValReborn);

      dis.close();
      in.close();

    } catch (Exception ex) {
      System.out.println("**EXCEPTION**" + ex.getMessage());
      fail();
    } finally {
      dis = null;
      in = null;
    }
  }

  @Test
  public void bb_value_equals() {
    OptionalBlob sVal1 = new OptionalBlob(TEST_BYTES1);
    OptionalBlob sVal2 = new OptionalBlob(TEST_BYTES1);
    OptionalBlob sVal3 = new OptionalBlob(TEST_BYTES2);

    assert (sVal1.equals(sVal2));
    assert (!sVal1.equals(sVal3));
  }

  @Test
  public void bc_value_copy() {
    OptionalBlob sVal1 = new OptionalBlob(TEST_BYTES1);
    OptionalBlob sVal2 = new OptionalBlob(sVal1);

    Assertions.assertNotSame(sVal1, sVal2);
    // Test that this is a different array of bytes with the same values
    Assertions.assertNotSame(sVal1.getData(), sVal2.getData());
    assertEquals(sVal1, sVal2);
  }
}
