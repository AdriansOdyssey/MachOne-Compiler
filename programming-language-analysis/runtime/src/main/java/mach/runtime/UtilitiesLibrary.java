package mach.runtime;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import static mach.runtime.RuntimeSupport.*;

/** Synchronous time values and pure text/bit utilities. No scheduler is involved. */
public final class UtilitiesLibrary {
    private UtilitiesLibrary() {}
    /** Decimal seconds avoid imposing the host's fixed-width duration limit. */
    public record DurationValue(BigDecimal seconds) {
        public DurationValue { Objects.requireNonNull(seconds); }
        @Override public String toString() { return seconds.stripTrailingZeros().toPlainString()+"s"; }
    }
    /** An opaque monotonic time point, deliberately unrelated to wall-clock dates. */
    public record InstantValue(long nanos) {
        @Override public String toString() { return "Instant{"+nanos+"ns}"; }
    }
    private static final Set<String> INTEGERS=Set.of("byte","ubyte","short","ushort","int","uint","long","ulong");
    private static final Map<String,BigDecimal> UNITS=Map.of(
        "seconds",BigDecimal.ONE,"minutes",BigDecimal.valueOf(60),"hours",BigDecimal.valueOf(3600),
        "days",BigDecimal.valueOf(86400),"millis",new BigDecimal("0.001"));
    public static String runtimeType(Object value) {
        return value instanceof DurationValue?"Duration":value instanceof InstantValue?"Instant":null;
    }
    private static boolean number(Object value) { return value instanceof NumberValue||value instanceof LargeInteger; }
    private static BigInteger integerValue(Object value) {
        return value instanceof LargeInteger n?n.value():BigInteger.valueOf(((NumberValue)value).integral());
    }
    private static BigDecimal decimal(Object value) {
        if(value instanceof LargeInteger n)return new BigDecimal(n.value());
        var n=(NumberValue)value;return n.floating()?BigDecimal.valueOf(n.real()):BigDecimal.valueOf(n.integral());
    }
    private static int width(Object value) {
        return switch(typeName(value)){case "byte","ubyte"->8;case "short","ushort"->16;case "int","uint"->32;default->64;};
    }
    private static BigInteger unsignedBits(Object value) {
        return integerValue(value).and(BigInteger.ONE.shiftLeft(width(value)).subtract(BigInteger.ONE));
    }
    public static Object dispatch(Context c,Object receiver,String selector,Object[] args,Object block) {
        try {
            if(number(receiver)) {
                if(args.length==0&&UNITS.containsKey(selector))return new DurationValue(decimal(receiver).multiply(UNITS.get(selector)));
                if(args.length==0&&selector.equals("format_thousands"))return thousands(decimal(receiver));
            }
            if(receiver instanceof DurationValue duration) {
                if(args.length==0)return switch(selector) {
                    case "as_seconds"->floating(duration.seconds().doubleValue(),"double");
                    case "as_millis"->integerText(duration.seconds().movePointRight(3).toBigInteger().toString(),"long");
                    case "to_str","stringify"->duration.toString();
                    default->UNHANDLED;
                };
                if(args.length==1) {
                    if(args[0] instanceof DurationValue other)return switch(selector) {
                        case "+"->new DurationValue(duration.seconds().add(other.seconds()));
                        case "-"->new DurationValue(duration.seconds().subtract(other.seconds()));
                        case "=="->duration.seconds().compareTo(other.seconds())==0;
                        case "<"->duration.seconds().compareTo(other.seconds())<0;
                        case ">"->duration.seconds().compareTo(other.seconds())>0;
                        case "<="->duration.seconds().compareTo(other.seconds())<=0;
                        case ">="->duration.seconds().compareTo(other.seconds())>=0;
                        default->UNHANDLED;
                    };
                    if(selector.equals("*")&&number(args[0]))return new DurationValue(duration.seconds().multiply(decimal(args[0])));
                }
            }
            if(receiver instanceof InstantValue instant) {
                if(args.length==0&&selector.equals("elapsed"))return new DurationValue(BigDecimal.valueOf(System.nanoTime()-instant.nanos(),9));
                if(args.length==1&&selector.equals("-")&&args[0] instanceof InstantValue other)
                    return new DurationValue(BigDecimal.valueOf(instant.nanos()-other.nanos(),9));
                if(args.length==1&&selector.equals("after")&&args[0] instanceof DurationValue duration) {
                    long nanos=duration.seconds().movePointRight(9).longValueExact();
                    return new InstantValue(Math.addExact(instant.nanos(),nanos));
                }
            }
            if(receiver instanceof TypeValue module) {
                if(module.name().equals("Time")&&selector.equals("now")&&args.length==0)return new InstantValue(System.nanoTime());
                if(module.name().equals("Text"))return text(selector,args);
                if(module.name().equals("Bits")&&args.length==1&&INTEGERS.contains(typeName(args[0]))) {
                    BigInteger bits=unsignedBits(args[0]);
                    if(selector.equals("count"))return integer(bits.bitCount(),"int");
                    if(selector.equals("parity"))return (bits.bitCount()&1)!=0;
                    if(selector.equals("reverse")) {
                        BigInteger result=BigInteger.ZERO;int width=width(args[0]);
                        for(int i=0;i<width;i++)if(bits.testBit(i))result=result.setBit(width-1-i);
                        return integerText(result.toString(),typeName(args[0]));
                    }
                }
                if(module.name().equals("Number")&&args.length==1) {
                    if(selector.equals("parse"))return parseNumber(c,(String)args[0]);
                    if(selector.equals("format_thousands")&&number(args[0]))return thousands(decimal(args[0]));
                }
            }
            return UNHANDLED;
        } catch(ArithmeticException|IllegalArgumentException exception) {
            return fail(c,"range",exception.getMessage()==null?"valor fora do intervalo":exception.getMessage());
        }
    }
    private static String thousands(BigDecimal number) {
        String value=number.toPlainString();int decimal=value.indexOf('.');if(decimal<0)decimal=value.length();
        int start=value.startsWith("-")?1:0;var out=new StringBuilder(value.length()+value.length()/3);
        for(int i=0;i<decimal;i++) {if(i>start&&(decimal-i)%3==0)out.append(',');out.append(value.charAt(i));}
        return out.append(value.substring(decimal)).toString();
    }
    private static Object parseNumber(Context context,String source) {
        String value=source.strip();
        if(value.matches("[+-]?[0-9]+")) {
            BigInteger integer=new BigInteger(value);
            if(integer.compareTo(BigInteger.valueOf(Integer.MIN_VALUE))>=0&&integer.compareTo(BigInteger.valueOf(Integer.MAX_VALUE))<=0)return integerText(value,"int");
            if(integer.compareTo(BigInteger.valueOf(Long.MIN_VALUE))>=0&&integer.compareTo(BigInteger.valueOf(Long.MAX_VALUE))<=0)return integerText(value,"long");
            if(integer.signum()>=0&&integer.bitLength()<=64)return integerText(value,"ulong");
            return fail(context,"range","inteiro fora dos tipos numéricos de MachOne");
        }
        if(!value.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?"))return fail(context,"parse","número inválido: "+source);
        double number=Double.parseDouble(value);return Double.isFinite(number)?floating(number,"double"):fail(context,"range","número fora do intervalo de double");
    }
    private static Object text(String selector,Object[] args) {
        if(selector.equals("levenshtein")&&args.length==2) {
            int[] left=((String)args[0]).codePoints().toArray(),right=((String)args[1]).codePoints().toArray();
            int[] before=new int[right.length+1],current=new int[right.length+1];for(int i=0;i<before.length;i++)before[i]=i;
            for(int i=0;i<left.length;i++) {current[0]=i+1;for(int j=0;j<right.length;j++)current[j+1]=Math.min(Math.min(before[j+1]+1,current[j]+1),before[j]+(left[i]==right[j]?0:1));int[] swap=before;before=current;current=swap;}
            return integer(before[right.length],"int");
        }
        if((selector.equals("indent:width")||selector.equals("indent"))&&args.length==2) {
            int width=integerValue(args[1]).intValueExact();if(width<0)throw new IllegalArgumentException("indentação negativa");
            String padding=" ".repeat(width),source=(String)args[0];return source.lines().map(line->padding+line).collect(java.util.stream.Collectors.joining("\n"))+(source.endsWith("\n")?"\n":"");
        }
        if(selector.equals("dedent")&&args.length==1) {
            String source=(String)args[0];List<String> lines=source.lines().toList();int width=Integer.MAX_VALUE;
            for(String line:lines)if(!line.isBlank()) {int indent=0;while(indent<line.length()&&(line.charAt(indent)==' '||line.charAt(indent)=='\t'))indent++;width=Math.min(width,indent);}
            int removed=width==Integer.MAX_VALUE?0:width;return lines.stream().map(line->line.substring(Math.min(removed,line.length()))).collect(java.util.stream.Collectors.joining("\n"))+(source.endsWith("\n")?"\n":"");
        }
        if(selector.equals("wrap:width")&&args.length==2) {
            int width=integerValue(args[1]).intValueExact();if(width<=0)throw new IllegalArgumentException("largura deve ser positiva");
            String source=(String)args[0];var output=new ArrayList<String>();
            for(String line:source.split("\n",-1)) {
                if(line.isBlank()){output.add("");continue;}
                var current=new StringBuilder();int length=0;
                for(String word:line.strip().split("\\s+")) {
                    int wordLength=word.codePointCount(0,word.length());
                    if(length>0&&length+1+wordLength>width){output.add(current.toString());current.setLength(0);length=0;}
                    if(length>0){current.append(' ');length++;}current.append(word);length+=wordLength;
                }
                output.add(current.toString());
            }
            return String.join("\n",output);
        }
        return UNHANDLED;
    }
}
