package io.izzel.arclight.common.mod.util;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.regex.Pattern;


public class ArclightTagHelper {


    public static final SimpleCommandExceptionType ERROR_TRAILING_DATA = new SimpleCommandExceptionType(Component.translatable("argument.nbt.trailing"));

    public static final SimpleCommandExceptionType ERROR_EXPECTED_KEY = new SimpleCommandExceptionType(Component.translatable("argument.nbt.expected.key"));

    public static final SimpleCommandExceptionType ERROR_EXPECTED_VALUE = new SimpleCommandExceptionType(Component.translatable("argument.nbt.expected.value"));

    public static final Dynamic2CommandExceptionType ERROR_INSERT_MIXED_LIST;

    public static final Dynamic2CommandExceptionType ERROR_INSERT_MIXED_ARRAY;

    public static final DynamicCommandExceptionType ERROR_INVALID_ARRAY;

    static {
        ERROR_INSERT_MIXED_LIST = new Dynamic2CommandExceptionType((error1, error2) -> Component.translatable("argument.nbt.list.mixed", new Object[]{error1, error2}));
        ERROR_INSERT_MIXED_ARRAY = new Dynamic2CommandExceptionType((error, error2) -> Component.translatable("argument.nbt.array.mixed", new Object[]{error, error2}));
        ERROR_INVALID_ARRAY = new DynamicCommandExceptionType(error -> Component.translatable("argument.nbt.array.invalid", new Object[]{error}));
    }

    private static final Pattern DOUBLE_PATTERN_NOSUFFIX = Pattern.compile("[-+]?(?:[0-9]+[.]|[0-9]*[.][0-9]+)(?:e[-+]?[0-9]+)?", 2);

    private static final Pattern DOUBLE_PATTERN = Pattern.compile("[-+]?(?:[0-9]+[.]?|[0-9]*[.][0-9]+)(?:e[-+]?[0-9]+)?d", 2);

    private static final Pattern FLOAT_PATTERN = Pattern.compile("[-+]?(?:[0-9]+[.]?|[0-9]*[.][0-9]+)(?:e[-+]?[0-9]+)?f", 2);

    private static final Pattern BYTE_PATTERN = Pattern.compile("[-+]?(?:0|[1-9][0-9]*)b", 2);

    private static final Pattern LONG_PATTERN = Pattern.compile("[-+]?(?:0|[1-9][0-9]*)l", 2);

    private static final Pattern SHORT_PATTERN = Pattern.compile("[-+]?(?:0|[1-9][0-9]*)s", 2);

    private static final Pattern INT_PATTERN = Pattern.compile("[-+]?(?:0|[1-9][0-9]*)");

    private final StringReader reader;

    public static CompoundTag getTagFromJson(String jsonString) throws CommandSyntaxException {
        return (new ArclightTagHelper(new StringReader(jsonString))).readSingleStruct();
    }

    @VisibleForTesting
    CompoundTag readSingleStruct() throws CommandSyntaxException {
        CompoundTag compoundnbt = readStruct();
        this.reader.skipWhitespace();
        if (this.reader.canRead())
            throw ERROR_TRAILING_DATA.createWithContext(this.reader);
        return compoundnbt;
    }

    public ArclightTagHelper(StringReader readerIn) {
        this.reader = readerIn;
    }

    protected String readKey() throws CommandSyntaxException {
        this.reader.skipWhitespace();
        if (!this.reader.canRead())
            throw ERROR_EXPECTED_KEY.createWithContext(this.reader);
        return this.reader.readString();
    }

    protected Tag readTypedValue() throws CommandSyntaxException {
        this.reader.skipWhitespace();
        int i = this.reader.getCursor();
        if (StringReader.isQuotedStringStart(this.reader.peek()))
            return StringTag.valueOf(this.reader.readQuotedString());
        String s = this.reader.readUnquotedString();
        if (s.isEmpty()) {
            this.reader.setCursor(i);
            throw ERROR_EXPECTED_VALUE.createWithContext(this.reader);
        }
        return type(s);
    }

    private Tag type(String stringIn) {
        try {
            if (FLOAT_PATTERN.matcher(stringIn).matches())
                return FloatTag.valueOf(Float.parseFloat(stringIn.substring(0, stringIn.length() - 1)));
            if (BYTE_PATTERN.matcher(stringIn).matches())
                return ByteTag.valueOf(Byte.parseByte(stringIn.substring(0, stringIn.length() - 1)));
            if (LONG_PATTERN.matcher(stringIn).matches())
                return LongTag.valueOf(Long.parseLong(stringIn.substring(0, stringIn.length() - 1)));
            if (SHORT_PATTERN.matcher(stringIn).matches())
                return ShortTag.valueOf(Short.parseShort(stringIn.substring(0, stringIn.length() - 1)));
            if (INT_PATTERN.matcher(stringIn).matches())
                return IntTag.valueOf(Integer.parseInt(stringIn));
            if (DOUBLE_PATTERN.matcher(stringIn).matches())
                return DoubleTag.valueOf(Double.parseDouble(stringIn.substring(0, stringIn.length() - 1)));
            if (DOUBLE_PATTERN_NOSUFFIX.matcher(stringIn).matches())
                return DoubleTag.valueOf(Double.parseDouble(stringIn));
            if ("true".equalsIgnoreCase(stringIn))
                return ByteTag.ONE;
            if ("false".equalsIgnoreCase(stringIn))
                return ByteTag.ZERO;
        } catch (NumberFormatException numberFormatException) {
        }
        return StringTag.valueOf(stringIn);
    }

    public Tag readValue() throws CommandSyntaxException {
        this.reader.skipWhitespace();
        if (!this.reader.canRead())
            throw ERROR_EXPECTED_VALUE.createWithContext(this.reader);
        char c0 = this.reader.peek();
        if (c0 == '{')
            return readStruct();
        return (c0 == '[') ? readList() : readTypedValue();
    }

    protected Tag readList() throws CommandSyntaxException {
        return (this.reader.canRead(3) && !StringReader.isQuotedStringStart(this.reader.peek(1)) && this.reader.peek(2) == ';') ? readArrayTag() : readListTag();
    }

    public CompoundTag readStruct() throws CommandSyntaxException {
        expect('{');
        CompoundTag compoundnbt = new CompoundTag();
        this.reader.skipWhitespace();
        while (this.reader.canRead() && this.reader.peek() != '}') {
            int i = this.reader.getCursor();
            String s = readKey();
            if (s.isEmpty()) {
                this.reader.setCursor(i);
                throw ERROR_EXPECTED_KEY.createWithContext(this.reader);
            }
            expect(':');
            compoundnbt.put(s, readValue());
            if (!hasElementSeparator())
                break;
            if (!this.reader.canRead())
                throw ERROR_EXPECTED_KEY.createWithContext(this.reader);
        }
        expect('}');
        return compoundnbt;
    }

    private Tag readListTag() throws CommandSyntaxException {
        expect('[');
        this.reader.skipWhitespace();
        if (!this.reader.canRead())
            throw ERROR_EXPECTED_VALUE.createWithContext(this.reader);
        ListTag listnbt = new ListTag();
        TagType<?> Tagtype = null;
        while (this.reader.peek() != ']') {
            int i = this.reader.getCursor();
            Tag Tag = readValue();
            TagType<?> Tagtype1 = Tag.getType();
            if (Tagtype == null) {
                Tagtype = Tagtype1;
            } else if (Tagtype1 != Tagtype) {
                this.reader.setCursor(i);
                throw ERROR_INSERT_MIXED_LIST.createWithContext(this.reader, Tagtype1.getName(), Tagtype.getName());
            }
            listnbt.add(Tag);
            if (!hasElementSeparator())
                break;
            if (!this.reader.canRead())
                throw ERROR_EXPECTED_VALUE.createWithContext(this.reader);
        }
        expect(']');
        return listnbt;
    }

    private Tag readArrayTag() throws CommandSyntaxException {
        expect('[');
        int i = this.reader.getCursor();
        char c0 = this.reader.read();
        this.reader.read();
        this.reader.skipWhitespace();
        if (!this.reader.canRead())
            throw ERROR_EXPECTED_VALUE.createWithContext(this.reader);
        if (c0 == 'B')
            return new ByteArrayTag(getNumberList(ByteArrayTag.TYPE, ByteTag.TYPE));
        if (c0 == 'L')
            return new LongArrayTag(getNumberList(LongArrayTag.TYPE, LongTag.TYPE));
        if (c0 == 'I')
            return new IntArrayTag(getNumberList(IntArrayTag.TYPE, IntTag.TYPE));
        this.reader.setCursor(i);
        throw ERROR_INVALID_ARRAY.createWithContext(this.reader, String.valueOf(c0));
    }

    private <T extends Number> List<T> getNumberList(TagType<?> arrayType, TagType<?> numberType) throws CommandSyntaxException {
        List<T> list = Lists.newArrayList();
        while (this.reader.peek() != ']') {
            int i = this.reader.getCursor();
            Tag Tag = readValue();
            TagType<?> Tagtype = Tag.getType();
            if (Tagtype != numberType) {
                this.reader.setCursor(i);
                throw ERROR_INSERT_MIXED_ARRAY.createWithContext(this.reader, Tagtype.getName(), arrayType.getName());
            }
            if (numberType == ByteTag.TYPE) {
                list.add((T) Byte.valueOf(((NumericTag) Tag).getAsByte()));
            } else if (numberType == LongTag.TYPE) {
                list.add((T) Long.valueOf(((NumericTag) Tag).getAsLong()));
            } else {
                list.add((T) Integer.valueOf(((NumericTag) Tag).getAsInt()));
            }
            if (hasElementSeparator())
                if (!this.reader.canRead())
                    throw ERROR_EXPECTED_VALUE.createWithContext(this.reader);
        }
        expect(']');
        return list;
    }

    private boolean hasElementSeparator() {
        this.reader.skipWhitespace();
        if (this.reader.canRead() && this.reader.peek() == ',') {
            this.reader.skip();
            this.reader.skipWhitespace();
            return true;
        }
        return false;
    }

    private void expect(char expected) throws CommandSyntaxException {
        this.reader.skipWhitespace();
        this.reader.expect(expected);
    }
}

