package mach.runtime;

import java.math.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import static mach.runtime.RuntimeSupport.*;

/** Native leaves of the standard library; source impls are resolved before these leaves. */
public final class CoreLibrary {
    private CoreLibrary() {}
    public record RandomValue(java.util.Random generator) {}
    private static final java.util.Random RANDOM=new java.security.SecureRandom();
    private static final Set<String> INTEGERS=Set.of("byte","ubyte","short","ushort","int","uint","long","ulong");
    public static Object dispatch(Context c,Object receiver,String selector,Object[] args,Object block) {
        if(selector.equals("\\\\"))selector="delete";
        try {
            Object utility=UtilitiesLibrary.dispatch(c,receiver,selector,args,block);if(utility!=UNHANDLED)return utility;
            if(receiver==null) {
                if(args.length==1&&(selector.equals("print")||selector.equals("eprint"))){(selector.equals("print")?System.out:System.err).print(stringify(args[0]));return null;}
                if(selector.equals("ask")&&args.length==1)return unary("read",args[0]);
               if(selector.equals("exit")&&(args.length==0||args.length==1)){System.exit(args.length==0?0:index(args[0]));return null;}
               if(selector.equals("assert:msg")&&args.length==2)return truth(args[0])?null:fail(c,"assertion",stringify(args[1]));
            }
            if(receiver instanceof TypeValue t) {
                if(t.name().equals("Math"))return math(c,selector,args);
                if(t.name().equals("Random"))return random(c,RANDOM,selector,args);
                if(t.name().equals("Fn")) {
                    if(selector.equals("identity")&&args.length==0)return new NativeFunction("lambda[any,any]",values->values[0]);
                    if(selector.equals("constant")&&args.length==1)return new NativeFunction("lambda["+typeName(args[0])+"]",values->args[0]);
                }
                if(args.length==0&&Set.of("max_value","min_value").contains(selector)) {
                    String type=t.name();boolean maximum=selector.equals("max_value");
                    if(type.equals("double"))return floating(maximum?Double.MAX_VALUE:-Double.MAX_VALUE,type);
                    if(type.equals("float"))return floating(maximum?Float.MAX_VALUE:-Float.MAX_VALUE,type);
                    if(INTEGERS.contains(type)){int width=bits(type);boolean unsigned=type.startsWith("u");return integerText((maximum?BigInteger.ONE.shiftLeft(unsigned?width:width-1).subtract(BigInteger.ONE):unsigned?BigInteger.ZERO:BigInteger.ONE.shiftLeft(width-1).negate()).toString(),type);}
                }
            }
            if(receiver instanceof RandomValue random)return random(c,random.generator(),selector,args);
            if(number(receiver)){Object value=numeric(c,receiver,selector,args,block);if(value!=UNHANDLED)return value;}
            if(receiver instanceof String text){Object value=text(c,text,selector,args);if(value!=UNHANDLED)return value;}
            if(receiver instanceof CharacterValue ch){Object value=characterMessage(c,ch,selector,args);if(value!=UNHANDLED)return value;}
            if(receiver instanceof Pattern regex){Object value=regexMessage(c,regex,selector,args);if(value!=UNHANDLED)return value;}
            if(receiver instanceof Boolean value) {
                if(selector.equals("xor")&&args.length==1)return value^truth(args[0]);
                if(selector.equals("to_int")&&args.length==0)return integer(value?1:0,"int");
                if(selector.equals("then:otherwise")&&args.length==2)return evaluate(c,args[value?0:1]);
                if(selector.equals("then")||selector.equals("otherwise")){Object callback=block!=null?block:args.length==1?args[0]:UNHANDLED;if(callback!=UNHANDLED)return value==selector.equals("then")?evaluate(c,callback):null;}
                if((selector.equals("and_then")||selector.equals("or_else"))&&(args.length==1||block!=null))return selector.equals("and_then")==value?evaluate(c,block!=null?block:args[0]):value;
            }
            if(callable(receiver)){Object value=function(c,receiver,selector,args);if(value!=UNHANDLED)return value;}
            if(selector.equals("unwrap_or")&&args.length==1){if(receiver instanceof FailureValue){acknowledge(c,receiver);return args[0];}return receiver;}
            if(selector.equals("unwrap")&&args.length==0){if(receiver instanceof FailureValue failure){acknowledge(c,failure);throw new MachFailure("unhandled",failure.message());}return receiver;}
            if((selector.equals("maybe_value")||selector.equals("to_maybe"))&&args.length==0){if(receiver instanceof FailureValue){acknowledge(c,receiver);return null;}return receiver;}
            if(selector.equals("to_try")&&args.length==1){if(receiver!=null)return receiver;return args[0] instanceof FailureValue?args[0]:fail(c,"not_found",stringify(args[0]));}
            if(receiver instanceof FailureValue failure) {
                if(args.length==0){if(selector.equals("message"))return failure.message();if(selector.equals("cause"))return failure.cause();if(selector.equals("trace"))return vector(failure.trace().toArray());}
                if(selector.equals("wrap")&&args.length==1){acknowledge(c,failure);return failWithCause(c,failure.kind(),stringify(args[0]),failure);}
            }
            if(!(receiver instanceof List<?>)&&!(receiver instanceof Map<?,?>)&&Set.of("or_else","and_then","map").contains(selector)&&(args.length==1||block!=null)) {
                Object callback=block!=null?block:args[0];boolean failed=receiver instanceof FailureValue;
                if(selector.equals("or_else")){if(failed){acknowledge(c,receiver);return invoke(c,callback,receiver);}return receiver;}
                return failed||receiver==null?receiver:invoke(c,callback,receiver);
            }
            if(args.length==0) {
                if(selector.equals("copy"))return receiver instanceof RecordValue record?new RecordValue(c,record.info,typeName(record),recordValues(record)):receiver;
                if(selector.equals("inspect")||selector.equals("to_str"))return stringify(receiver);
                if(selector.equals("nil?"))return receiver==null;
                if(selector.equals("present?"))return receiver!=null&&(!(receiver instanceof String text)||!text.isEmpty())&&(!(receiver instanceof Collection<?> values)||!values.isEmpty())&&(!(receiver instanceof Map<?,?> values)||!values.isEmpty());
                if(selector.equals("frozen?"))return true;
                if(selector.equals("hash"))return integer(stableHash(receiver),"long");
            }
            if((selector.equals("tap")||selector.equals("then_do"))&&(args.length==1||block!=null)){Object value=invoke(c,block!=null?block:args[0],receiver);return value instanceof FailureValue?value:selector.equals("tap")?receiver:value;}
            return UNHANDLED;
        }catch(ClassCastException error){return fail(c,"no_applicable","tipos incompatíveis na mensagem "+selector);}
        catch(ArithmeticException|IllegalArgumentException|IndexOutOfBoundsException error){return fail(c,"invalid",error.getMessage()==null?"argumento inválido para "+selector:error.getMessage());}
    }
    private static boolean number(Object value){return value instanceof NumberValue||value instanceof LargeInteger;}
    private static boolean floatingNumber(Object value){return value instanceof NumberValue n&&n.floating();}
    private static BigInteger integral(Object value){return value instanceof LargeInteger n?n.value():BigInteger.valueOf(((NumberValue)value).integral());}
    private static double real(Object value){return value instanceof LargeInteger n?n.value().doubleValue():((NumberValue)value).real();}
    private static int bits(String type){return switch(type){case "byte","ubyte"->8;case "short","ushort"->16;case "int","uint"->32;default->64;};}
    private static int index(Object value){return integral(value).intValueExact();}
    private static int compare(Object a,Object b){return floatingNumber(a)||floatingNumber(b)?Double.compare(real(a),real(b)):integral(a).compareTo(integral(b));}
    private static Object integerLike(Object receiver,BigInteger value){return integerText(value.toString(),typeName(receiver));}
    private static Object numericLike(Object receiver,double value){return floatingNumber(receiver)?floating(value,typeName(receiver)):integerLike(receiver,BigDecimal.valueOf(value).toBigInteger());}
    private static Object invoke(Context c,Object fn,Object...args){return send(c,fn,"result",args);}
    public static boolean callable(Object value){return value instanceof Closure||value instanceof NativeFunction;}
    private static Object evaluate(Context c,Object value){return callable(value)?invoke(c,value):value;}
    private static Object numeric(Context c,Object x,String s,Object[] a,Object block) {
        double value=real(x);String type=typeName(x);
        if(a.length==0)switch(s) {
            case "abs":return floatingNumber(x)?floating(Math.abs(value),type):integerLike(x,integral(x).abs());
            case "zero?":return value==0;case "positive?":return value>0;case "negative?":return value<0;
            case "sign":return integer(Double.isNaN(value)||value==0?0:value<0?-1:1,"int");
            case "succ":return floatingNumber(x)?numericLike(x,value+1):integerLike(x,integral(x).add(BigInteger.ONE));
            case "pred":return floatingNumber(x)?numericLike(x,value-1):integerLike(x,integral(x).subtract(BigInteger.ONE));
            case "sqrt":return value<0?fail(c,"range","raiz quadrada de número negativo"):floating(Math.sqrt(value),"double");
            case "cbrt":return floating(Math.cbrt(value),"double");
            case "to_double":return floating(value,"double");case "to_float":return floating(value,"float");
            case "to_int":return integerText((floatingNumber(x)?BigDecimal.valueOf(value).toBigInteger():integral(x)).toString(),"int");
            case "to_long":return integerText((floatingNumber(x)?BigDecimal.valueOf(value).toBigInteger():integral(x)).toString(),"long");
            case "to_str":return stringify(x);
            case "round":return integerText(BigDecimal.valueOf(value).setScale(0,RoundingMode.HALF_UP).toBigInteger().toString(),"int");
            case "floor":return integerText(BigDecimal.valueOf(Math.floor(value)).toBigInteger().toString(),"int");
            case "ceil":return integerText(BigDecimal.valueOf(Math.ceil(value)).toBigInteger().toString(),"int");
            case "trunc":return integerText(BigDecimal.valueOf(value).toBigInteger().toString(),"int");
            case "nan?":return Double.isNaN(value);case "infinite?":return Double.isInfinite(value);case "finite?":return Double.isFinite(value);
        }
        if(a.length==0&&!floatingNumber(x))switch(s) {
            case "even?":return !integral(x).testBit(0);case "odd?":return integral(x).testBit(0);case "prime?":return prime(integral(x));
            case "digits":return integral(x).abs().toString().chars().mapToObj(n->integer(n-'0',"int")).toList();
            case "bit_length":return integer(integral(x).abs().bitLength(),"int");
            case "to_char":{int n=index(x);return Character.isValidCodePoint(n)&&!(n>=0xd800&&n<=0xdfff)?new CharacterValue(n):fail(c,"range","código Unicode inválido");}
            case "factorial":{BigInteger n=integral(x);if(n.signum()<0||n.compareTo(BigInteger.valueOf(20))>0)return fail(c,"range","factorial fora do intervalo de ulong");BigInteger product=BigInteger.ONE;for(int i=2;i<=n.intValue();i++)product=product.multiply(BigInteger.valueOf(i));return integerText(product.toString(),"ulong");}
        }
        if(s.equals("times")&&(block!=null||a.length==1&&callable(a[0]))){Object callback=block!=null?block:a[0];for(BigInteger n=BigInteger.ZERO;n.compareTo(integral(x))<0;n=n.add(BigInteger.ONE)){Object result=invoke(c,callback);if(result instanceof FailureValue)return result;}return null;}
        if(Set.of("upto","downto","step:every").contains(s)&&a.length==(s.equals("step:every")?2:1)&&block!=null){BigInteger start=integral(x),end=integral(a[0]),step=s.equals("upto")?BigInteger.ONE:s.equals("downto")?BigInteger.ONE.negate():integral(a[1]);if(step.signum()==0)return fail(c,"range","passo não pode ser zero");for(BigInteger n=start;step.signum()>0?n.compareTo(end)<=0:n.compareTo(end)>=0;n=n.add(step)){Object result=invoke(c,block,integerLike(x,n));if(result instanceof FailureValue)return result;}return null;}
        if(a.length==1) {
            if(s.equals("format")){String spec=stringify(a[0]);if(!spec.matches("\\.\\d{1,3}"))return fail(c,"invalid","formato numérico inválido");return String.format(Locale.ROOT,"%"+spec+"f",value);}
            if(s.equals("to_str")&&!floatingNumber(x)){int base=index(a[0]);return base>=2&&base<=36?integral(x).toString(base):fail(c,"range","base deve estar entre 2 e 36");}
            if(s.equals("round"))return numericLike(x,BigDecimal.valueOf(value).setScale(index(a[0]),RoundingMode.HALF_UP).doubleValue());
            if(number(a[0]))switch(s) {
                case "min":return compare(x,a[0])<=0?x:a[0];case "max":return compare(x,a[0])>=0?x:a[0];
                case "pow":return binary(c,"**",x,a[0]);case "div":return binary(c,"//",x,a[0]);case "mod","rem":return binary(c,"%",x,a[0]);
                case "divmod":{Object q=binary(c,"//",x,a[0]);if(q instanceof FailureValue)return q;Object r=binary(c,"%",x,a[0]);return vector(new Object[]{q,r});}
                case "gcd":return integerText(integral(x).gcd(integral(a[0])).toString(),"int");
                case "lcm":{BigInteger l=integral(x),r=integral(a[0]);return integerText((l.signum()==0||r.signum()==0?BigInteger.ZERO:l.divide(l.gcd(r)).multiply(r).abs()).toString(),"int");}
            }
        }
        if(a.length==2){if(s.equals("clamp"))return compare(x,a[0])<0?a[0]:compare(x,a[1])>0?a[1]:x;if(s.equals("between?"))return compare(x,a[0])>=0&&compare(x,a[1])<=0;if(s.equals("pow:mod"))return integerLike(x,integral(x).modPow(integral(a[0]),integral(a[1])));}
        return UNHANDLED;
    }
    private static Object text(Context c,String s,String sel,Object[] a) {
        int length=s.codePointCount(0,s.length());
        if(a.length==0)switch(sel) {
            case "head":return s.isEmpty()?null:new CharacterValue(s.codePointAt(0));case "tail":return s.isEmpty()?s:s.substring(s.offsetByCodePoints(0,1));
            case "length","size":return integer(length,"int");case "upcase":return s.toUpperCase(Locale.ROOT);case "downcase":return s.toLowerCase(Locale.ROOT);
            case "capitalize":return s.isEmpty()?s:s.substring(0,s.offsetByCodePoints(0,1)).toUpperCase(Locale.ROOT)+s.substring(s.offsetByCodePoints(0,1)).toLowerCase(Locale.ROOT);
            case "swapcase":{var out=new StringBuilder();s.codePoints().forEach(n->out.appendCodePoint(Character.isUpperCase(n)?Character.toLowerCase(n):Character.toUpperCase(n)));return out.toString();}
            case "title_case":{var out=new StringBuilder();boolean start=true;for(int n:s.codePoints().toArray()){out.appendCodePoint(start?Character.toTitleCase(n):Character.toLowerCase(n));start=!Character.isLetterOrDigit(n);}return out.toString();}
            case "trim":return s.strip();case "trim_left":return s.stripLeading();case "trim_right":return s.stripTrailing();
            case "squeeze":{var out=new StringBuilder();int last=-1;for(int n:s.codePoints().toArray()){if(n!=last)out.appendCodePoint(n);last=n;}return out.toString();}
            case "reverse":{int[] points=s.codePoints().toArray();var out=new StringBuilder();for(int i=points.length-1;i>=0;i--)out.appendCodePoint(points[i]);return out.toString();}
            case "empty?":return s.isEmpty();case "blank?":return s.isBlank();
            case "digit?":return !s.isEmpty()&&s.codePoints().allMatch(Character::isDigit);case "alpha?":return !s.isEmpty()&&s.codePoints().allMatch(Character::isLetter);case "alnum?":return !s.isEmpty()&&s.codePoints().allMatch(Character::isLetterOrDigit);case "space?":return !s.isEmpty()&&s.codePoints().allMatch(Character::isWhitespace);
            case "upper?":return s.codePoints().anyMatch(Character::isLetter)&&s.codePoints().filter(Character::isLetter).allMatch(Character::isUpperCase);case "lower?":return s.codePoints().anyMatch(Character::isLetter)&&s.codePoints().filter(Character::isLetter).allMatch(Character::isLowerCase);
            case "split_lines","lines":return s.lines().map(x->(Object)x).toList();case "words":return s.isBlank()?List.of():Arrays.stream(s.strip().split("\\s+")).map(x->(Object)x).toList();
            case "chars":return s.codePoints().mapToObj(n->(Object)new CharacterValue(n)).toList();case "bytes":{var out=new ArrayList<Object>();for(byte value:s.getBytes(StandardCharsets.UTF_8))out.add(integer(value&255,"ubyte"));return List.copyOf(out);}
            case "to_tag","to_sym":return tag(s);case "escape":return escape(s);case "unescape":return unescape(s);
        }
        if(sel.equals("delete")&&a.length>0){String result=s;for(Object target:a)result=result.replace(stringify(target),"");return result;}
        if(a.length==1)switch(sel) {
            case "<":return comparePoints(s,(String)a[0])<0;case ">":return comparePoints(s,(String)a[0])>0;case "<=":return comparePoints(s,(String)a[0])<=0;case ">=":return comparePoints(s,(String)a[0])>=0;case "<<":return s+stringify(a[0]);
            case "nth":{
                if(a[0] instanceof List<?> indices){
                    var out=new StringBuilder();
                    for(Object key:indices){
                        int at=index(key);
                        if(at<1||at>length)return fail(c,"index","índice fora da string");
                        out.appendCodePoint(s.codePointAt(s.offsetByCodePoints(0,at-1)));
                    }
                    return out.toString();
                }
                int at=index(a[0]);
                if(at<1||at>length)return fail(c,"index","índice fora da string");
                return new CharacterValue(s.codePointAt(s.offsetByCodePoints(0,at-1)));
            }
            case "repeat":return s.repeat(index(a[0]));case "center":{int pad=Math.max(0,index(a[0])-length);return " ".repeat(pad/2)+s+" ".repeat(pad-pad/2);}
            case "has?":return s.contains(stringify(a[0]));case "starts_with?":return s.startsWith(stringify(a[0]));case "ends_with?":return s.endsWith(stringify(a[0]));
            case "index_of","last_index_of":{int at=sel.equals("index_of")?s.indexOf(stringify(a[0])):s.lastIndexOf(stringify(a[0]));return at<0?null:integer(s.codePointCount(0,at),"int");}
            case "count":{String target=stringify(a[0]);if(target.isEmpty())return integer(length+1,"int");int count=0,at=0;while((at=s.indexOf(target,at))>=0){count++;at+=target.length();}return integer(count,"int");}
            case "match?":return ((Pattern)a[0]).matcher(s).find();case "match":return match((Pattern)a[0],s);case "scan":return scan((Pattern)a[0],s);
            case "take":return slice(s,0,Math.max(0,Math.min(length,index(a[0]))));case "drop":return slice(s,Math.max(0,Math.min(length,index(a[0]))),length);
            case "split":{String sep=stringify(a[0]);return sep.isEmpty()?s.codePoints().mapToObj(n->(Object)new String(Character.toChars(n))).toList():Arrays.stream(s.split(Pattern.quote(sep),-1)).map(x->(Object)x).toList();}
            case "compare":return integer(comparePoints(s,stringify(a[0])),"int");case "casecmp":return integer(Integer.signum(s.compareToIgnoreCase(stringify(a[0]))),"int");
        }
        if(a.length==2)switch(sel) {
            case "replace:with":return s.replace(stringify(a[0]),stringify(a[1]));case "replace_first:with":return s.replaceFirst(Pattern.quote(stringify(a[0])),Matcher.quoteReplacement(stringify(a[1])));
            case "slice:to":{int from=index(a[0]),to=index(a[1]);if(from<0)from+=length;if(to<0)to+=length;return slice(s,from,to);}
            case "pad_left:with","pad_right:with":{String padding=stringify(a[1]);if(padding.isEmpty())throw new IllegalArgumentException("preenchimento vazio");int needed=Math.max(0,index(a[0])-length),width=padding.codePointCount(0,padding.length());String fill=slice(padding.repeat((needed+width-1)/width),0,needed);return sel.startsWith("pad_left")?fill+s:s+fill;}
        }
        return UNHANDLED;
    }
    private static int comparePoints(String left,String right){int[] a=left.codePoints().toArray(),b=right.codePoints().toArray();for(int i=0;i<Math.min(a.length,b.length);i++)if(a[i]!=b[i])return Integer.compare(a[i],b[i]);return Integer.compare(a.length,b.length);}
    private static String slice(String s,int from,int to){return s.substring(s.offsetByCodePoints(0,from),s.offsetByCodePoints(0,to));}
    private static boolean prime(BigInteger n){if(n.compareTo(BigInteger.TWO)<0)return false;if(n.equals(BigInteger.TWO)||n.equals(BigInteger.valueOf(3)))return true;if(!n.testBit(0))return false;BigInteger d=n.subtract(BigInteger.ONE);int shifts=d.getLowestSetBit();d=d.shiftRight(shifts);for(long witness:new long[]{2,325,9375,28178,450775,9780504,1795265022}){BigInteger a=BigInteger.valueOf(witness).mod(n);if(a.signum()==0)continue;BigInteger value=a.modPow(d,n);if(value.equals(BigInteger.ONE)||value.equals(n.subtract(BigInteger.ONE)))continue;boolean passed=false;for(int r=1;r<shifts;r++){value=value.multiply(value).mod(n);if(value.equals(n.subtract(BigInteger.ONE))){passed=true;break;}}if(!passed)return false;}return true;}
    private static String escape(String s){return s.replace("\\","\\\\").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t").replace("\"","\\\"");}
    private static String unescape(String s){var out=new StringBuilder();for(int i=0;i<s.length();i++){char value=s.charAt(i);if(value=='\\'&&i+1<s.length()){value=s.charAt(++i);out.append(switch(value){case 'n'->'\n';case 'r'->'\r';case 't'->'\t';default->value;});}else out.append(value);}return out.toString();}
    private static Object characterMessage(Context c,CharacterValue ch,String s,Object[] a){if(a.length!=0)return UNHANDLED;int n=ch.codepoint();return switch(s){case "code"->integer(n,"int");case "upcase"->new CharacterValue(Character.toUpperCase(n));case "downcase"->new CharacterValue(Character.toLowerCase(n));case "digit?"->Character.isDigit(n);case "alpha?"->Character.isLetter(n);case "space?"->Character.isWhitespace(n);case "to_str"->ch.toString();case "to_int"->Character.digit(n,10)<0?fail(c,"parse","char não é dígito"):integer(Character.digit(n,10),"int");case "succ","pred"->{int next=n+(s.equals("succ")?1:-1);if(next==0xd800)next=0xe000;if(next==0xdfff)next=0xd7ff;yield Character.isValidCodePoint(next)?new CharacterValue(next):fail(c,"range","limite Unicode excedido");}default->UNHANDLED;};}
    private static Object regexMessage(Context c,Pattern p,String s,Object[] a){if(a.length==0)return switch(s){case "source"->p.pattern();case "flags"->integer(p.flags(),"int");default->UNHANDLED;};if(a.length==1)return switch(s){case "match?"->p.matcher((String)a[0]).find();case "match"->match(p,(String)a[0]);case "scan"->scan(p,(String)a[0]);case "split"->Arrays.stream(p.split((String)a[0],-1)).map(x->(Object)x).toList();default->UNHANDLED;};return a.length==2&&s.equals("replace:with")?p.matcher((String)a[0]).replaceAll((String)a[1]):UNHANDLED;}
    private static Object match(Pattern p,String s){Matcher m=p.matcher(s);if(!m.find())return null;var groups=new LinkedHashMap<Object,Object>();for(int i=0;i<=m.groupCount();i++)groups.put(tag(Integer.toString(i)),m.group(i));for(String name:m.namedGroups().keySet())groups.put(tag(name),m.group(name));return Collections.unmodifiableMap(groups);}
    private static Object scan(Pattern p,String s){var values=new ArrayList<Object>();Matcher m=p.matcher(s);while(m.find())values.add(m.group());return List.copyOf(values);}
    private static List<String> functionTypes(Object fn){String text=fn instanceof Closure closure?closure.signature():((NativeFunction)fn).signature();if(!text.startsWith("lambda[")||!text.endsWith("]"))return List.of("any");var types=new ArrayList<String>();int depth=0,start=7;for(int i=start;i<text.length()-1;i++){char ch=text.charAt(i);if(ch=='[')depth++;else if(ch==']')depth--;else if(ch==','&&depth==0){types.add(text.substring(start,i));start=i+1;}}types.add(text.substring(start,text.length()-1));return types;}
    private static Object nativeFunction(List<String> types,java.util.function.Function<Object[],Object> body){return new NativeFunction("lambda["+String.join(",",types)+"]",body);}
    private static Object function(Context c,Object f,String s,Object[] a){List<String> types=functionTypes(f);int arity=types.size()-1;if(s.equals("arity")&&a.length==0)return integer(arity,"int");if(s.equals("memoize")&&a.length==0){var cache=new HashMap<List<Object>,Object>();return nativeFunction(types,values->{List<Object> key=Collections.unmodifiableList(Arrays.asList(values.clone()));if(cache.containsKey(key))return cache.get(key);Object value=invoke(c,f,values);if(!(value instanceof FailureValue))cache.put(key,value);return value;});}if(s.equals("flip")&&a.length==0&&arity==2)return nativeFunction(List.of(types.get(1),types.get(0),types.get(2)),values->invoke(c,f,values[1],values[0]));if(s.equals("partial")&&a.length<=arity){Object[] bound=a.clone();return nativeFunction(types.subList(bound.length,types.size()),values->{Object[] all=Arrays.copyOf(bound,bound.length+values.length);System.arraycopy(values,0,all,bound.length,values.length);return invoke(c,f,all);});}if(s.equals("curry")&&a.length==0)return curry(c,f,types,new Object[0]);if((s.equals("compose")||s.equals("then_do"))&&a.length==1&&callable(a[0])){Object first=s.equals("compose")?a[0]:f,second=s.equals("compose")?f:a[0];List<String> in=functionTypes(first),out=functionTypes(second);var combined=new ArrayList<>(in.subList(0,in.size()-1));combined.add(out.getLast());return nativeFunction(combined,values->{Object value=invoke(c,first,values);return value instanceof FailureValue?value:invoke(c,second,value);});}return UNHANDLED;}
    private static Object curry(Context c,Object f,List<String> types,Object[] bound){if(types.size()<=2)return bound.length==0?f:nativeFunction(types,values->{Object[] all=Arrays.copyOf(bound,bound.length+values.length);System.arraycopy(values,0,all,bound.length,values.length);return invoke(c,f,all);});String result="lambda["+String.join(",",types.subList(1,types.size()))+"]";return nativeFunction(List.of(types.getFirst(),result),values->{Object[] next=Arrays.copyOf(bound,bound.length+1);next[bound.length]=values[0];return curry(c,f,types.subList(1,types.size()),next);});}
    private static long stableHash(Object value){if(value==null)return 0;if(value instanceof List<?> list){long hash=1;for(Object item:list)hash=31*hash+stableHash(item);return hash;}if(value instanceof Map<?,?> map){long hash=0;for(var entry:map.entrySet())hash+=stableHash(entry.getKey())^stableHash(entry.getValue());return hash;}if(value instanceof NumberValue n)return 31L*n.type().hashCode()+(n.floating()?Double.doubleToLongBits(n.real()):Long.hashCode(n.integral()));if(value instanceof LargeInteger n)return 31L*n.type().hashCode()+n.value().hashCode();return value.hashCode();}
    private static Object math(Context c,String s,Object[] a){if(a.length==0)return switch(s){case "pi"->floating(Math.PI,"double");case "e"->floating(Math.E,"double");case "tau"->floating(2*Math.PI,"double");case "infinity"->floating(Double.POSITIVE_INFINITY,"double");case "nan"->floating(Double.NaN,"double");default->UNHANDLED;};if(a.length==1&&number(a[0])){double x=real(a[0]);Double value=switch(s){case "sin"->Math.sin(x);case "cos"->Math.cos(x);case "tan"->Math.tan(x);case "asin"->Math.asin(x);case "acos"->Math.acos(x);case "atan"->Math.atan(x);case "sinh"->Math.sinh(x);case "cosh"->Math.cosh(x);case "tanh"->Math.tanh(x);case "exp"->Math.exp(x);case "ln"->Math.log(x);case "log2"->Math.log(x)/Math.log(2);case "log10"->Math.log10(x);case "sqrt"->Math.sqrt(x);case "floor"->Math.floor(x);case "ceil"->Math.ceil(x);case "round"->(double)Math.round(x);case "abs"->Math.abs(x);case "to_radians"->Math.toRadians(x);case "to_degrees"->Math.toDegrees(x);default->null;};if(value!=null){boolean invalid=Set.of("ln","log2","log10").contains(s)&&x<=0||s.equals("sqrt")&&x<0||Set.of("asin","acos").contains(s)&&Math.abs(x)>1;return invalid?fail(c,"range","argumento fora do domínio de "+s):floating(value,"double");}}
        if(a.length==2&&number(a[0])&&number(a[1])){double x=real(a[0]),y=real(a[1]);return switch(s){case "atan2:x"->floating(Math.atan2(x,y),"double");case "hypot:b"->floating(Math.hypot(x,y),"double");case "log:base"->x<=0||y<=0||y==1?fail(c,"range","domínio inválido do logaritmo"):floating(Math.log(x)/Math.log(y),"double");case "min"->floating(Math.min(x,y),"double");case "max"->floating(Math.max(x,y),"double");default->UNHANDLED;};}
        if(a.length==1&&a[0] instanceof List<?> values&&Set.of("sum","mean","median","variance","stddev").contains(s)){if(values.isEmpty()&&!s.equals("sum"))return fail(c,"range","coleção vazia");double[] nums=values.stream().mapToDouble(CoreLibrary::real).sorted().toArray();double sum=Arrays.stream(nums).sum(),mean=sum/nums.length;double result=switch(s){case "sum"->sum;case "mean"->mean;case "median"->nums.length%2==1?nums[nums.length/2]:(nums[nums.length/2-1]+nums[nums.length/2])/2;case "variance","stddev"->{double variance=Arrays.stream(nums).map(x->(x-mean)*(x-mean)).sum()/nums.length;yield s.equals("stddev")?Math.sqrt(variance):variance;}default->throw new IllegalStateException();};return floating(result,"double");}return UNHANDLED;}
    private static Object random(Context c,java.util.Random random,String s,Object[] a){if(a.length==0)return switch(s){case "double"->floating(random.nextDouble(),"double");case "bool"->random.nextBoolean();case "uuid"->new UUID(random.nextLong(),random.nextLong()).toString();default->UNHANDLED;};if(a.length==1)return switch(s){case "seed"->new RandomValue(new java.util.Random(integral(a[0]).longValue()));case "pick"->{List<?> values=(List<?>)a[0];yield values.isEmpty()?null:values.get(random.nextInt(values.size()));}case "shuffle"->{var values=new ArrayList<Object>((List<?>)a[0]);Collections.shuffle(values,random);yield Collections.unmodifiableList(values);}case "bytes"->{byte[] bytes=new byte[index(a[0])];random.nextBytes(bytes);var values=new ArrayList<Object>();for(byte value:bytes)values.add(integer(value&255,"ubyte"));yield List.copyOf(values);}default->UNHANDLED;};if(s.equals("int")&&a.length==2)return integer(random.nextLong(integral(a[0]).longValueExact(),integral(a[1]).longValueExact()),typeName(a[0]));return UNHANDLED;}
}
