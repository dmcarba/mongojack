package org.mongodb.jackson.internal;

import org.bson.BSONObject;
import org.codehaus.jackson.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Parses a BSONObject by traversing it.  This class was copied from
 * {@link org.codehaus.jackson.node.TreeTraversingParser} and then adapted to be for BSONObject's, rather than JsonNode's.
 */
public class BSONObjectTraversingParser extends JsonParser {
    /*
    /**********************************************************
    /* Configuration
    /**********************************************************
     */

    protected ObjectCodec objectCodec;

    /**
     * Traversal context within tree
     */
    protected BSONObjectCursor nodeCursor;

    /*
    /**********************************************************
    /* State
    /**********************************************************
     */

    /**
     * Sometimes parser needs to buffer a single look-ahead token; if so,
     * it'll be stored here. This is currently used for handling
     */
    protected JsonToken nextToken;

    /**
     * Flag needed to handle recursion into contents of child
     * Array/Object nodes.
     */
    protected boolean startContainer;

    /**
     * Flag that indicates whether parser is closed or not. Gets
     * set when parser is either closed by explicit call
     * ({@link #close}) or when end-of-input is reached.
     */
    protected boolean closed;

    /*
    /**********************************************************
    /* Life-cycle
    /**********************************************************
     */

    public BSONObjectTraversingParser(BSONObject o) {
        this(o, null);
    }

    public BSONObjectTraversingParser(BSONObject o, ObjectCodec codec) {
        super(0);
        objectCodec = codec;
        if (o instanceof Iterable) {
            nextToken = JsonToken.START_ARRAY;
            nodeCursor = new BSONObjectCursor.ArrayCursor((Iterable) o, null);
        } else {
            nextToken = JsonToken.START_OBJECT;
            nodeCursor = new BSONObjectCursor.ObjectCursor(o, null);
        }
    }

    @Override
    public void setCodec(ObjectCodec c) {
        objectCodec = c;
    }

    @Override
    public ObjectCodec getCodec() {
        return objectCodec;
    }

    /*
    /**********************************************************
    /* Closeable implementation
    /**********************************************************
     */

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            nodeCursor = null;
            _currToken = null;
        }
    }

    /*
    /**********************************************************
    /* Public API, traversal
    /**********************************************************
     */

    @Override
    public JsonToken nextToken() throws IOException {
        if (nextToken != null) {
            _currToken = nextToken;
            nextToken = null;
            return _currToken;
        }
        // are we to descend to a container child?
        if (startContainer) {
            startContainer = false;
            // minor optimization: empty containers can be skipped
            if (!nodeCursor.currentHasChildren()) {
                _currToken = (_currToken == JsonToken.START_OBJECT) ?
                        JsonToken.END_OBJECT : JsonToken.END_ARRAY;
                return _currToken;
            }
            nodeCursor = nodeCursor.iterateChildren();
            _currToken = nodeCursor.nextToken();
            if (_currToken == JsonToken.START_OBJECT || _currToken == JsonToken.START_ARRAY) {
                startContainer = true;
            }
            return _currToken;
        }
        // No more content?
        if (nodeCursor == null) {
            closed = true; // if not already set
            return null;
        }
        // Otherwise, next entry from currentFieldName cursor
        _currToken = nodeCursor.nextToken();
        if (_currToken != null) {
            if (_currToken == JsonToken.START_OBJECT || _currToken == JsonToken.START_ARRAY) {
                startContainer = true;
            }
            return _currToken;
        }
        // null means no more children; need to return end marker
        _currToken = nodeCursor.endToken();
        nodeCursor = nodeCursor.getParent();
        return _currToken;
    }

    @Override
    public JsonParser skipChildren() throws IOException {
        if (_currToken == JsonToken.START_OBJECT) {
            startContainer = false;
            _currToken = JsonToken.END_OBJECT;
        } else if (_currToken == JsonToken.START_ARRAY) {
            startContainer = false;
            _currToken = JsonToken.END_ARRAY;
        }
        return this;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    /*
    /**********************************************************
    /* Public API, token accessors
    /**********************************************************
     */

    @Override
    public String getCurrentName() {
        return (nodeCursor == null) ? null : nodeCursor.getCurrentName();
    }

    @Override
    public JsonStreamContext getParsingContext() {
        return nodeCursor;
    }

    @Override
    public JsonLocation getTokenLocation() {
        return JsonLocation.NA;
    }

    @Override
    public JsonLocation getCurrentLocation() {
        return JsonLocation.NA;
    }

    /*
    /**********************************************************
    /* Public API, access to textual content
    /**********************************************************
     */

    @Override
    public String getText() {
        if (closed) {
            return null;
        }
        // need to separate handling a bit...
        switch (_currToken) {
            case FIELD_NAME:
                return nodeCursor.getCurrentName();
            case VALUE_STRING:
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT:
            case VALUE_EMBEDDED_OBJECT:
                return currentNode().toString();
        }

        return _currToken.asString();
    }

    @Override
    public char[] getTextCharacters() throws IOException {
        return getText().toCharArray();
    }

    @Override
    public int getTextLength() throws IOException {
        return getText().length();
    }

    @Override
    public int getTextOffset() throws IOException {
        return 0;
    }

    @Override
    public boolean hasTextCharacters() {
        // generally we do not have efficient access as char[], hence:
        return false;
    }

    /*
    /**********************************************************
    /* Public API, typed non-text access
    /**********************************************************
     */

    //public byte getByteValue() throws IOException

    @Override
    public NumberType getNumberType() throws IOException {
        Object n = currentNode();
        if (n instanceof Integer) {
            return NumberType.INT;
        } else if (n instanceof BigInteger) {
            return NumberType.BIG_INTEGER;
        } else if (n instanceof BigDecimal) {
            return NumberType.BIG_DECIMAL;
        } else if (n instanceof Double) {
            return NumberType.DOUBLE;
        } else if (n instanceof Float) {
            return NumberType.FLOAT;
        } else if (n instanceof Long) {
            return NumberType.LONG;
        } else {
            throw _constructError(n + " is not a number");
        }
    }

    @Override
    public BigInteger getBigIntegerValue() throws IOException {
        Number n = currentNumericNode();
        if (n instanceof BigInteger) {
            return (BigInteger) n;
        } else {
            return BigInteger.valueOf(n.longValue());
        }
    }

    @Override
    public BigDecimal getDecimalValue() throws IOException {
        Number n = currentNumericNode();
        if (n instanceof BigDecimal) {
            return (BigDecimal) n;
        } else {
            return BigDecimal.valueOf(n.doubleValue());
        }
    }

    @Override
    public double getDoubleValue() throws IOException {
        return currentNumericNode().doubleValue();
    }

    @Override
    public float getFloatValue() throws IOException {
        return currentNumericNode().floatValue();
    }

    @Override
    public long getLongValue() throws IOException {
        return currentNumericNode().longValue();
    }

    @Override
    public int getIntValue() throws IOException {

        return currentNumericNode().intValue();
    }

    @Override
    public Number getNumberValue() throws IOException {
        return currentNumericNode();
    }

    private Number currentNumericNode() throws JsonParseException {
        Object n = currentNode();
        if (n instanceof Number) {
            return (Number) n;
        } else {
            throw _constructError(n + " is not a number");
        }
    }

    /*
    /**********************************************************
    /* Public API, typed binary (base64) access
    /**********************************************************
     */

    @Override
    public byte[] getBinaryValue(Base64Variant b64variant) throws IOException {
        Object n = currentNode();
        if (n instanceof byte[]) {
            return (byte[]) n;
        } else if (n instanceof org.bson.types.ObjectId) {
            return ((org.bson.types.ObjectId) n).toByteArray();
        }
        return null;
    }

    /*
    /**********************************************************
    /* Internal methods
    /**********************************************************
     */

    protected Object currentNode() {
        if (closed || nodeCursor == null) {
            return null;
        }
        return nodeCursor.currentNode();
    }
}

