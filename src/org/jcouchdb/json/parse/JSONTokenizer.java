package org.jcouchdb.json.parse;

/**
 * JSON Tokenizer. Parses the json text into {@link Token}s, can push back one
 * token, is stateful / not reusable / not thread safe.
 *
 * @author shelmberger
 *
 */
public class JSONTokenizer
{
    private char[] json;
    private int index;
    private boolean isDecimal;

    private Token headToken = new Token(TokenType.NULL, null);
    private Token curToken = headToken;

    public JSONTokenizer(String json)
    {
        if (json == null)
        {
            throw new IllegalArgumentException("json string cannot be null.");
        }

        this.json = json.toCharArray();
    }

    /**
     * Returns <code>true</code> if the given character is a number character.
     */
    private boolean isNumberCharacter(char c )
    {
        switch(c)
        {
            case '.':
            case '-':
            case '+':
            case 'E':
            case 'e':
                isDecimal = true;
                return true;
            default:
                return c >= '0' && c <= '9';
        }
    }

    private void ensureKeyword(String word)
    {
        if (index + word.length() > json.length || !new String(json,index,word.length()).equals(word) )
        {
            throw new JSONParseException("invalid keyword "+info()+" (should be "+word+")");
        }
        index += word.length();
    }
    /**
     * Returns the next token.
     * If there are no more tokens, a token with {@link TokenType#END} will be returned
     * @return
     */
    public Token next()
    {
        if (curToken != null && curToken.next != null)
        {
            curToken = curToken.next;
            return curToken;
        }

        skipWhiteSpace();

        if (index >= json.length)
        {
            return new Token(TokenType.END);
        }

        isDecimal = false;

        Token token ;

        int start = index;
        char c1 = nextChar();
        switch(c1)
        {
            case '"':
            case '\'':
            {
                token = parseString();
                break;
            }
            case '[':
                token = new Token(TokenType.BRACKET_OPEN, "[");
                break;
            case ']':
                token = new Token(TokenType.BRACKET_CLOSE, "]");
                break;
            case '{':
                token = new Token(TokenType.BRACE_OPEN, "{");
                break;
            case '}':
                token = new Token(TokenType.BRACE_CLOSE, "}");
                break;
            case ':':
                token = new Token(TokenType.COLON, ":");
                break;
            case ',':
                token = new Token(TokenType.COMMA, ",");
                break;
            case 't':
                ensureKeyword("rue");
                token = new Token(TokenType.TRUE, Boolean.TRUE);
                break;
            case 'f':
                ensureKeyword("alse");
                token = new Token(TokenType.FALSE, Boolean.FALSE);
                break;
            case 'n':
                ensureKeyword("ull");
                token = new Token(TokenType.NULL);
                break;
            default:
            {
                if ( isNumberCharacter(c1))
                {
                    token = parseNumber(c1);
                    break;
                }
                throw new JSONParseException("Unexpected character '"+c1+"'");
            }
        }

        curToken.next = token;
        token.prev = curToken;
        curToken = token;

        return token;
    }

    /**
     * Pushes back the given Token. This will reset the tokenizer to the index before the
     * token was encountered and the next {@link #next()} call will return the same token again.
     *
     * @param   t
     */
    public void pushBack(Token oldToken)
    {
        if (oldToken.prev == null)
        {
            throw new IllegalStateException("oldToken.prev cannot be null");
        }

        curToken = oldToken.prev;
    }

    public void reset()
    {
        curToken = headToken;
    }

    private Token parseNumber(char c1)
    {
        if ( c1 == '-')
        {
            isDecimal = false;
        }

        int start = index-1;
        while( index < json.length)
        {
            char c = nextChar();
            if (!isNumberCharacter(c))
            {
                back();
                break;
            }
        }

        String number = new String(json, start, index-start);

        if (isDecimal)
        {
            return parseDecimal(number);
        }
        else
        {
            try
            {
                long l = Long.parseLong(number );
                return new Token(TokenType.INTEGER, l);
            }
            catch(NumberFormatException nfe)
            {
                // must be a integer greater than Long.MAX_VALUE
                // convert to decimal
                return parseDecimal(number);
            }
        }
    }

    private Token parseDecimal(String number)
    {
        try
        {
            double d = Double.parseDouble(number);
            return new Token(TokenType.DECIMAL, d);
        }
        catch(NumberFormatException nfe)
        {
            throw new JSONParseException("Error parsing double "+number);
        }
    }

    private Token parseString()
    {
        index--;
        char quoteChar = this.nextChar();
        StringBuilder sb = new StringBuilder();
        boolean escape = false;
        boolean endOfString = false;
        while (index < json.length)
        {
            char c = nextChar();


            if ((endOfString = (c == quoteChar && !escape)))
            {
                break;
            }

            if (c == '\\')
            {
                if (escape)
                {
                    sb.append('\\');
                }
                escape = !escape;
            }
            else if (escape)
            {
                switch(c)
                {
                    case '\'':
                    case '"':
                    case '/':
                        sb.append(c);
                        break;
                    case 'b':
                        sb.append('\b');
                        break;
                    case 'f':
                        sb.append('\f');
                        break;
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'u':
                        if (index + 4 > json.length)
                        {
                            throw new JSONParseException("unexpected end of unicode sequence");
                        }
                        char unicode = (char) Integer.parseInt(new String(json,index,4), 16);
                        index += 4;
                        sb.append(unicode);
                        break;
                    default:
                        throw new JSONParseException("Illegal escape character "+c+" / "+Integer.toHexString(c));
                }
                escape = false;
            }
            else
            {
                if (Character.isISOControl(c))
                {
                    throw new JSONParseException("Illegal control character 0x"+Integer.toHexString(c));
                }
                sb.append(c);
            }
        }

        if (endOfString)
        {
            return new Token(TokenType.STRING, sb.toString());
        }
        else
        {
            throw new JSONParseException("Unexpected end of string, missing quote "+info());
        }
    }

    private String info()
    {
        int column = 1, line = 1;

        boolean isCR,wasCR = false;

        for (int i=0; i < json.length; i++)
        {
            char c = json[i];
            isCR = isCR(c);
            if (wasCR && !isCR)
            {
                line++;
                column = 0;
            }
            else
            {
                column++;
            }
            wasCR = isCR;
        }


        return "at line "+line+", column "+column;
    }

    private boolean isCR(char c)
    {
        return c == '\r' || c == '\n';
    }

    private char nextChar()
    {
        return json[index++];
    }

    /**
     * Goes back one char.
     */
    private void back()
    {
        index--;
    }

    private void skipWhiteSpace()
    {
        while (index < json.length)
        {
            char c = nextChar();

            switch(c)
            {
                case ' ':
                case '\r':
                case '\b':
                case '\n':
                case '\t':
                    break;
                default:
                    back();
                    return;
            }
        }
    }

    /**
     * Expects the next token to be of one of the given token types
     *
     * @param tokenizer
     * @param types
     * @return
     * @throws JSONParseException if the expectation is not fulfilled
     */
    public Token expectNext(TokenType... types)
    {
        Token t = next();
        t.expect(types);
        return t;
    }
}
