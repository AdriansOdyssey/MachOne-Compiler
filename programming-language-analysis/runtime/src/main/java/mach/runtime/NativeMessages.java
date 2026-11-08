package mach.runtime;
import java.util.*;
import java.util.regex.Pattern;
import static mach.runtime.RuntimeSupport.*;
/** Side-effect-free native selector metadata; reflection never probes by executing operations. */
final class NativeMessages {
    private NativeMessages() {}
    private static Set<String> selectors(String text){return Set.of(text.split(" "));}
    private static final Set<String> UNIVERSAL=selectors("type stringify inspect copy hash nil? present? tap then_do frozen? responds_to? perform:with perform fail? ok? eq == != like ~ unwrap unwrap_or maybe_value to_maybe to_try");
    private static final Set<String> NUMBER=selectors("+ - * / // % ** < > <= >= abs zero? positive? negative? sign succ pred min max clamp between? divmod div mod rem pow pow:mod sqrt cbrt to_double to_float to_int to_long to_str format round floor ceil trunc nan? infinite? finite?");
    private static final Set<String> INTEGER=selectors("&&& ||| ^^^ ~~~ <<< >>> even? odd? prime? digits bit_length to_char factorial gcd lcm times upto downto step:every");
    private static final Set<String> TEXT=selectors("+ * << \\\\ nth head tail upcase downcase capitalize swapcase title_case trim trim_left trim_right squeeze center pad_left:with pad_right:with reverse repeat length size empty? blank? has? starts_with? ends_with? index_of last_index_of count match? match scan digit? alpha? alnum? space? upper? lower? replace:with replace_first:with delete slice:to take drop split split_lines lines words chars bytes to_tag to_sym escape unescape compare casecmp to_str");
    private static final Set<String> VECTOR=selectors("+ * << >> \\\\ nth head tail element_at first last length size has? has_value? has_all? has_any? empty? any? none? index_of last_index_of count sample append unshift push insert:at merge pop shift delete delete_nth copy put:at compact concat each each_with_index collect map select filter reject reduce:with fold:with inject:with sum product min max min_by max_by sort sort_by sort_with reverse uniq flatten flat_map zip unzip take drop take_while drop_while slice:to find detect find_index all? one? group_by partition tally each_slice each_cons chunk_while rotate shuffle combinations permutations join to_obj intersect union difference sum_by average binary_search to_vec to_set");
    private static final Set<String> OBJECT=selectors("| << \\\\ 's head tail get maybe_get get_element element_at has_key? has_value? has? length size empty? keys values pairs fetch:default dig key_of copy copy_with delete delete_key merge deep_merge update:with select filter reject collect map map_values map_keys filter_keys except slice invert to_vec each reduce:with fold:with inject:with all? any? none? one? count find detect find_index min_by max_by sort_by group_by partition");
    private static final Map<String,Set<String>> MODULES=Map.ofEntries(
        Map.entry("Math",selectors("pi e tau infinity nan sin cos tan asin acos atan atan2:x sinh cosh tanh exp ln log:base log2 log10 sqrt hypot:b floor ceil round abs min max to_radians to_degrees sum mean median variance stddev")),
        Map.entry("Fn",selectors("identity constant")),Map.entry("Random",selectors("int double bool pick shuffle seed bytes uuid")),
        Map.entry("File",selectors("read read_lines write:text append:text exists? delete copy:to move:to size each_line open:mode")),
        Map.entry("Path",selectors("join base dir ext absolute exists? file? dir?")),Map.entry("Dir",selectors("list create delete glob cwd home tmp")),
        Map.entry("Env",selectors("get set all")),Map.entry("Process",selectors("args pid run:args cwd")),Map.entry("System",selectors("os arch cpus hostname version")),
        Map.entry("Console",selectors("clear color:text width")),Map.entry("Json",selectors("parse generate pretty")),Map.entry("Csv",selectors("parse:header generate")),
        Map.entry("Base64",selectors("encode decode")),Map.entry("Hex",selectors("encode decode")),Map.entry("Hash",selectors("sha256 sha1 md5")),Map.entry("Uuid",selectors("v4 parse")),Map.entry("Url",selectors("encode decode parse")),
        Map.entry("Date",selectors("today new parse")),Map.entry("DateTime",selectors("now utc parse")),Map.entry("Reflect",selectors("methods attrs supertypes subtypes responds_to send")),
        Map.entry("Debug",selectors("dump trace assert:msg")),Map.entry("Log",selectors("level info warn error debug")),Map.entry("Test",selectors("suite test expect:to_be expect_fail:kind run")),Map.entry("Bench",selectors("run:times"))
    );
    static boolean supports(Object receiver,String selector){
        if(UNIVERSAL.contains(selector))return true;
        if(!(receiver instanceof List<?>)&&!(receiver instanceof Map<?,?>)&&Set.of("or_else","and_then","map").contains(selector))return true;
        if(receiver instanceof TypeValue type){
            if(type.name().equals("Time")&&selector.equals("now"))return true;
            if(type.name().equals("Number")&&Set.of("parse","format_thousands").contains(selector))return true;
            if(type.name().equals("Text")&&Set.of("wrap:width","indent:width","indent","dedent","levenshtein").contains(selector))return true;
            if(type.name().equals("Bits")&&Set.of("count","reverse","parity").contains(selector))return true;
            Set<String> module=MODULES.get(type.name());if(module!=null&&module.contains(selector))return true;
            if(Set.of("name","attrs","methods","derives?","supertypes","subtypes").contains(selector))return true;
            if(type.name().equals("Fail")&&selector.equals("raise"))return true;
            if((type.name().equals("Vector")||type.name().startsWith("vec["))&&Set.of("new","create").contains(selector))return true;
            return Set.of("max_value","min_value").contains(selector)&&Set.of("byte","ubyte","short","ushort","int","uint","long","ulong","float","double").contains(type.name());
        }
        if(receiver instanceof NumberValue number)return NUMBER.contains(selector)||!number.floating()&&INTEGER.contains(selector)||Set.of("seconds","minutes","hours","days","millis","format_thousands").contains(selector);
        if(receiver instanceof LargeInteger)return NUMBER.contains(selector)||INTEGER.contains(selector)||Set.of("seconds","minutes","hours","days","millis","format_thousands").contains(selector);
        if(receiver instanceof String)return TEXT.contains(selector);if(receiver instanceof List<?>)return VECTOR.contains(selector);if(receiver instanceof Map<?,?>)return OBJECT.contains(selector);
        if(receiver instanceof Boolean)return Set.of("then","otherwise","then:otherwise","xor","to_int","not","and","or","&&","||").contains(selector);
        if(receiver instanceof CharacterValue)return Set.of("code","upcase","downcase","digit?","alpha?","space?","to_str","to_int","succ","pred").contains(selector);
        if(receiver instanceof Pattern)return Set.of("match","match?","scan","replace:with","split","source","flags").contains(selector);
        if(receiver instanceof Closure||receiver instanceof NativeFunction)return Set.of("result","arity","curry","compose","partial","memoize","flip","then_do").contains(selector);
        if(receiver instanceof MessageValue)return Set.of("'s","proceed","proceed_with","redirect").contains(selector);
        if(receiver instanceof FailureValue)return Set.of("kind","message","cause","trace","wrap").contains(selector);
        if(receiver instanceof CoreLibrary.RandomValue)return MODULES.get("Random").contains(selector);
        if(receiver instanceof SystemLibrary.DateValue)return Set.of("year","month","day","weekday","leap?","to_str","add_days","add_months","format","days_until","<",">","<=",">=").contains(selector);
        if(receiver instanceof SystemLibrary.DateTimeValue)return Set.of("year","month","day","hour","minute","second","to_unix","utc","to_str","zone","format").contains(selector);
        if(receiver instanceof UtilitiesLibrary.DurationValue)return Set.of("+","-","*","==","<",">","<=",">=","as_seconds","as_millis","to_str").contains(selector);
        if(receiver instanceof UtilitiesLibrary.InstantValue)return Set.of("elapsed","-","after").contains(selector);
        if(receiver instanceof SystemLibrary.FileHandle)return Set.of("read","write","path").contains(selector);
        return false;
    }
    static Set<String> forType(String descriptor){
        String name=descriptor.contains("[")?descriptor.substring(0,descriptor.indexOf('[')):descriptor;
        var result=new TreeSet<String>(UNIVERSAL);
        if(Set.of("byte","ubyte","short","ushort","int","uint","long","ulong","float","double","Number","Integer","Signed","Unsigned","Real").contains(name)){
            result.addAll(NUMBER);result.addAll(Set.of("seconds","minutes","hours","days","millis","format_thousands"));
            if(Set.of("byte","ubyte","short","ushort","int","uint","long","ulong","Integer","Signed","Unsigned").contains(name))result.addAll(INTEGER);
        }
        if(name.equals("str"))result.addAll(TEXT);if(name.equals("vec"))result.addAll(VECTOR);if(name.equals("obj"))result.addAll(OBJECT);
        Set<String> module=MODULES.get(name);if(module!=null)result.addAll(module);
        switch(name){
            case "bool"->result.addAll(Set.of("then","otherwise","then:otherwise","xor","to_int","not","and","or","&&","||"));
            case "char"->result.addAll(Set.of("code","upcase","downcase","digit?","alpha?","space?","to_str","to_int","succ","pred"));
            case "regex"->result.addAll(Set.of("match","match?","scan","replace:with","split","source","flags"));
            case "lambda"->result.addAll(Set.of("result","arity","curry","compose","partial","memoize","flip"));
            case "message"->result.addAll(Set.of("'s","proceed","proceed_with","redirect"));
            case "fail"->result.addAll(Set.of("kind","message","cause","trace","wrap"));
            case "Time"->result.add("now");case "Number"->result.addAll(Set.of("parse","format_thousands"));
            case "Text"->result.addAll(Set.of("wrap:width","indent:width","indent","dedent","levenshtein"));
            case "Bits"->result.addAll(Set.of("count","reverse","parity"));
            case "Duration"->result.addAll(Set.of("+","-","*","==","<",">","<=",">=","as_seconds","as_millis","to_str"));
            case "Instant"->result.addAll(Set.of("elapsed","-","after"));
            default->{}
        }
        return Collections.unmodifiableSet(result);
    }

}
