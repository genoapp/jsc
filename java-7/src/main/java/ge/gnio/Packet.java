
/*
 * Copyright 2018  Geno Papashvili
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ge.gnio;


import ge.gnio.annotation.Obtain;
import ge.gnio.exception.IncompatiblePacketException;
import ge.gnio.exception.UnsupportedTypeException;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

@SuppressWarnings("UnusedReturnValue")
public class Packet implements Cloneable {


    private int key;

    private ByteBuffer buffer;

    public Packet(int key) {
        this(key, ByteBuffer.allocate(0).order(ByteOrder.LITTLE_ENDIAN));
    }

    public Packet(int key, ByteBuffer buffer) {
        this.key = key;
        this.buffer = buffer;
    }

    public Packet writeBoolean(boolean value) {
        return write((byte) (value ? 1 : 0));
    }

    public Packet writeString(String value) {
        char[] ch = value.toCharArray();
        int size = ch.length;
        writeInt(size);
        for (char c : ch) {
            writeChar(c);
        }
        return this;
    }

    public Packet writeChar(char value) {
        checkSize(Character.SIZE / Byte.SIZE);
        buffer.putChar(value);
        return this;
    }


    public Packet writeInt(int value) {
        checkSize(Integer.SIZE / Byte.SIZE);
        buffer.putInt(value);
        return this;
    }

    public Packet writeLong(long value) {
        checkSize(Long.SIZE / Byte.SIZE);
        buffer.putLong(value);
        return this;
    }

    public Packet writeDouble(double value) {
        checkSize(Double.SIZE / Byte.SIZE);
        buffer.putDouble(value);
        return this;
    }

    public Packet writeFloat(float value) {
        checkSize(Float.SIZE / Byte.SIZE);
        buffer.putFloat(value);
        return this;
    }

    public Packet writeShort(short value) {
        checkSize(Short.SIZE / Byte.SIZE);
        buffer.putShort(value);
        return this;
    }

    public Packet writeBytes(byte[] src) {
        checkSize(src.length);
        buffer.put(src);
        return this;
    }

    public Packet writeByte(byte value) {
        checkSize(1);
        buffer.put(value);
        return this;
    }

    public Packet writeBuffer(ByteBuffer buffer) {
        checkSize(buffer.limit());
        this.buffer.put(buffer);
        return this;
    }


    public byte readByte() {
        try {
            return buffer.get();
        } catch (BufferUnderflowException e) {
            throw new IncompatiblePacketException(e.getMessage());
        }
    }


    public Packet readBytes(byte[] b) {
        try {
            buffer.get(b);
            return this;
        } catch (BufferUnderflowException e) {
            throw new IncompatiblePacketException(e.getMessage());
        }
    }

    public boolean readBoolean() {
        try {
            return buffer.get() == 1;
        } catch (BufferUnderflowException e) {
            throw new IncompatiblePacketException(e.getMessage());
        }
    }

    public char readChar() {
        try {
            return buffer.getChar();
        } catch (BufferUnderflowException e) {
            throw new IncompatiblePacketException(e.getMessage());
        }
    }


    public short readShort() {
        try {
            return buffer.getShort();
        } catch (BufferUnderflowException e) {
            throw new IncompatiblePacketException(e.getMessage());
        }
    }

    public int readInt() {
        try {
            return buffer.getInt();
        } catch (BufferUnderflowException e) {
            throw new IncompatiblePacketException(e.getMessage());
        }
    }

    public long readLong() {
        try {
            return buffer.getLong();
        } catch (BufferUnderflowException e) {
            throw new IncompatiblePacketException(e.getMessage());
        }
    }

    public float readFloat() {
        try {
            return buffer.getFloat();
        } catch (BufferUnderflowException e) {
            throw new IncompatiblePacketException(e.getMessage());
        }
    }

    public double readDouble() {
        try {
            return buffer.getDouble();
        } catch (BufferUnderflowException e) {
            throw new IncompatiblePacketException(e.getMessage());
        }
    }

    public String readString() {
        StringBuilder builder = new StringBuilder();
        try {
            int size = readInt();
            for (int i = 0; i < size; i++) {
                builder.append(readChar());
            }
            return builder.toString();
        } catch (IncompatiblePacketException e) {
            throw new IncompatiblePacketException(e.getMessage());
        }
    }


    public Packet writeObject(Serializable target) {
        ByteArrayOutputStream ba = new ByteArrayOutputStream();

        try (ObjectOutputStream out = new ObjectOutputStream(ba)) {
            out.writeObject(target);
            byte[] bytes = ba.toByteArray();

            writeInt(bytes.length);
            writeBytes(bytes);

        } catch (IOException e) {
            e.printStackTrace();
        }
        return this;
    }


    public Object readObject() throws IncompatiblePacketException {
        Object target;

        try {
            int size = readInt();
            byte[] bytes = new byte[size];
            readBytes(bytes);

            ByteArrayInputStream ba = new ByteArrayInputStream(bytes);
            ObjectInputStream in = new ObjectInputStream(ba);
            target = in.readObject();
        } catch (ClassNotFoundException | IncompatiblePacketException | IOException e) {
            throw new IncompatiblePacketException(e.getMessage());
        }
        return target;
    }


    public <T> Packet write(T value) {
        if (value == null) {
            throw new NullPointerException();
        }
        switch (value.getClass().getSimpleName().toLowerCase()) {
            case "boolean":
                writeBoolean((Boolean) value);
                break;
            case "char":  //
            case "character":
                writeChar((Character) value);
                break;
            case "string":
                writeString((String) value);
                break;
            case "byte":
                writeByte((Byte) value);
                break;
            case "short":
                writeShort((Short) value);
                break;
            case "int":   //
            case "integer":
                writeInt((Integer) value);
                break;
            case "long":
                writeLong((Long) value);
                break;
            case "float":
                writeFloat((Float) value);
                break;
            case "double":
                writeDouble((Double) value);
                break;
            default:
                throw new UnsupportedTypeException(value.getClass().getName());
        }

        return this;
    }


    @SuppressWarnings("unchecked")
    public <T> T read(Class<T> mClass) {
        if (mClass == null) {
            throw new NullPointerException();
        }
        Object value;
        switch (mClass.getSimpleName().toLowerCase()) {
            case "boolean":
                value = readBoolean();
                break;
            case "char":
            case "character":
                value = readChar();
                break;
            case "string":
                value = readString();
                break;
            case "byte":
                value = readByte();
                break;
            case "short":
                value = readShort();
                break;
            case "int":
            case "integer":
                value = readInt();
                break;
            case "long":
                value = readLong();
                break;
            case "float":
                value = readFloat();
                break;
            case "double":
                value = readDouble();
                break;
            default:
                throw new UnsupportedTypeException(mClass.getName());
        }
        return (T) value; //unchecked cast
    }

    public <T> T toObject(Class<T> mClass) {
        try {

            Constructor<T> constructor = mClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            final T target = constructor.newInstance();


            List<Field> fields = Arrays.asList(target.getClass().getDeclaredFields());


            Collections.sort(fields, new Comparator<Field>() {
                @Override
                public int compare(Field a, Field b) {
                    return a.getName().compareTo(b.getName());
                }
            });

            for (Field f : fields) {
                if (f.isAnnotationPresent(Obtain.class) && f.getAnnotation(Obtain.class).value()) {

                    f.setAccessible(true);

                    f.set(target, read(f.getType()));

                }
            }
            return target;
        } catch (InstantiationException | InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException("No target class ready", e);
        }
    }


    public Packet fromObject(Object target) {


        List<Field> fields = Arrays.asList(target.getClass().getDeclaredFields());

        Collections.sort(fields, new Comparator<Field>() {
            @Override
            public int compare(Field a, Field b) {
                return a.getName().compareTo(b.getName());
            }
        });

        try {
            for (Field f : fields) {
                if (f.isAnnotationPresent(Obtain.class) && f.getAnnotation(Obtain.class).value()) {
                    f.setAccessible(true);
                    write(f.get(target));
                }
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException("", e);
        }
        return this;
    }

    private void checkSize(int size) {
        if (size > buffer.remaining()) {
            ByteBuffer tmp = ByteBuffer.allocate(buffer.limit() + Math.max(buffer.limit(), size)).order(ByteOrder.LITTLE_ENDIAN);
            buffer.flip();
            tmp.put(buffer);
            buffer = tmp;
        }
    }


    public Packet flip() {
        buffer.flip();
        return this;
    }

    public Packet rewind() {
        buffer.rewind();
        return this;
    }


    public Packet clear() {
        buffer.clear();
        return this;
    }


    public ByteBuffer getBuffer() {
        return buffer;
    }

    public int getKey() {
        return key;
    }

    @Override
    public String toString() {
        return buffer.toString();
    }

    @Override
    public final Packet clone() {
        Packet packet = this;
        try {
            packet = (Packet) super.clone();
        } catch (CloneNotSupportedException ignore) {

        }
        return packet;
    }
}