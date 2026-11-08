package mach.frontend;

import org.antlr.v4.runtime.*;
import java.util.*;

/** First-pass selector metadata used to disambiguate colon-free message sends. */
public final class MessageSignatures {
    public record Signature(List<String> parts,List<Integer> arities) {}
    private final List<Signature> signatures=new ArrayList<>();
    private final Map<String,Set<Integer>> arities=new HashMap<>();
    private final Set<String> compositionAliases=new HashSet<>();
    public boolean compositionAlias(String name){return compositionAliases.contains(name);}
    public MessageSignatures() {
        for(String s:List.of("type","even?","odd?","upcase","downcase","length","first","last","copy","ok?","fail?","kind","receive","close","stringify","create"))add(List.of(s),List.of(0));
        for(String s:List.of("new","result","raise","push","merge","has_all?","has_any?","has"))add(List.of(s),List.of(-1));
        for(String s:List.of("show","read","then","otherwise","append","unshift","delete","delete_nth","element_at","get","maybe_get","get_element","copy_with","has?","has_key?","has_value?","each","collect","select","reject","times","send","slot","maybe_slot"))add(List.of(s),List.of(1));
        add(List.of("then","otherwise"),List.of(1,1));
        add(List.of("insert","at"),List.of(1,1));
        add(List.of("perform","with"),List.of(1,1));
        for(String name:List.of("size","empty?","reverse","uniq","compact","flatten","sum","product","min","max","average","tally","keys","values","pairs","invert","to_vec","pop","shift","shuffle","unzip","permutations","words","chars","bytes","nil?","present?","frozen?","inspect","hash","slots","arity","curry","memoize","flip","lazy","abs","zero?","positive?","negative?","sign","succ","pred","sqrt","cbrt","factorial","prime?","digits","bit_length","to_double","to_float","to_int","to_long","to_str","to_char","round","floor","ceil","trunc","nan?","infinite?","finite?","max_value","min_value","capitalize","swapcase","title_case","trim","trim_left","trim_right","squeeze","blank?","split_lines","lines","to_tag","to_sym","escape","unescape","code","digit?","alpha?","alnum?","space?","upper?","lower?","source","flags","name","attrs","methods","supertypes","subtypes","proceed","target"))add(List.of(name),List.of(0));
        for(String name:List.of("print","eprint","ask","exit","upto","downto","step","concat","index_of","last_index_of","take","drop","rotate","sort_by","sort_with","min_by","max_by","flat_map","find","detect","find_index","all?","any?","none?","one?","group_by","partition","count","each_with_index","join","union","intersect","difference","each_slice","each_cons","combinations","binary_search","key_of","delete_key","except","filter_keys","slice","tap","then_do","and_then","or_else","xor","clamp","between?","divmod","div","mod","rem","pow","gcd","lcm","format","repeat","center","pad_left","pad_right","starts_with?","ends_with?","match?","match","scan","replace","replace_first","split","encode","decode","compare","casecmp","proceed_with","redirect","derives?","unwrap_or","to_try","compose","partial"))add(List.of(name),List.of(1));
        for(String name:List.of("count","all?","any?","none?","one?","sort","sample","unwrap","maybe_value","to_maybe"))add(List.of(name),List.of(0));
        for(String name:List.of("min","max","round","first","last","to_str","reverse","sqrt","abs","sum","product","floor","ceil"))add(List.of(name),List.of(1));
        for(String name:List.of("reduce","fold","inject","update","replace","replace_first","pad_left","pad_right"))add(List.of(name,"with"),List.of(1,1));
        add(List.of("put","at"),List.of(1,1));add(List.of("slice","to"),List.of(1,1));
        add(List.of("fetch","default"),List.of(1,1));add(List.of("step","every"),List.of(1,1));
        add(List.of("assert","msg"),List.of(1,1));add(List.of("pow","mod"),List.of(1,1));
        for(String name:List.of("today","now","utc","cwd","home","tmp","pid","args","os","arch","cpus","hostname","version","width","clear","all","v4","trace","run","year","month","day","weekday","leap?","hour","minute","second","to_unix","seconds","minutes","hours","days","millis","as_seconds","as_millis","elapsed","format_thousands"))add(List.of(name),List.of(0));
        for(String name:List.of("read_lines","exists?","list","glob","set","base","dir","ext","absolute","file?","dir?","parse","maybe_parse","generate","pretty","sha256","sha1","md5","methods","attrs","supertypes","subtypes","dump","info","warn","error","debug","level","each_line","zone","add_days","add_months","days_until","get","create","delete","size","read","after","parity","dedent","indent"))add(List.of(name),List.of(1));
        for(String name:List.of("levenshtein","put"))add(List.of(name),List.of(2));
        add(List.of("wrap","width"),List.of(1,1));add(List.of("indent","with"),List.of(1,1));
        add(List.of("insert","with"),List.of(1,1));add(List.of("range","to"),List.of(1,1));
        for(String name:List.of("add","subset?","push_front","push_back","enqueue"))add(List.of(name),List.of(1));
        for(String name:List.of("pop_front","pop_back","front","back","peek","dequeue","swap"))add(List.of(name),List.of(0));
        add(List.of("join"),List.of(-1));add(List.of("set"),List.of(2));
        for(var parts:List.of(List.of("write","text"),List.of("append","text"),List.of("copy","to"),List.of("move","to"),List.of("run","args"),List.of("color","text"),List.of("parse","header"),List.of("open","mode"),List.of("expect","to_be"),List.of("expect_fail","kind"),List.of("run","times")))add(parts,List.of(1,1));

    }
    public void add(List<String> parts,List<Integer> counts) {
        var signature=new Signature(List.copyOf(parts),List.copyOf(counts));
        if(!signatures.contains(signature))signatures.add(signature);
        for(int i=0;i<parts.size();i++)arities.computeIfAbsent(parts.get(i),x->new HashSet<>()).add(counts.get(i));
    }
    public boolean groupedPositionals(String part,int count) {
        Set<Integer> choices=arities.getOrDefault(part,Set.of());return count>1&&choices.contains(count)&&!choices.contains(1);
    }
    public List<Signature> signatures(){return Collections.unmodifiableList(signatures);}
    public void collect(List<? extends Token> tokens) {
        for(int start=0;start<tokens.size();) {
            int end=start;while(end<tokens.size()&&!Set.of("\n","\r\n",".").contains(text(tokens,end)))end++;
            var words=new ArrayList<String>();int i=start;while(i<end&&identifier(text(tokens,i)))words.add(text(tokens,i++));
            if(words.size()>=2) {
                var labels=new ArrayList<>(words.subList(0,words.size()-2));String field=words.get(words.size()-2).replaceFirst("^_","");
                boolean properties=false;for(int j=i;j<end;j++){if(text(tokens,j).equals("{"))properties=true;else if(properties&&identifier(text(tokens,j)))labels.add(text(tokens,j));}
                if(labels.contains("accessible")||labels.contains("readable")||labels.contains("configurable")){add(List.of(field),List.of(0));add(List.of(field),List.of(1));}
            }
            start=end+1;
        }
        for(int i=0;i<tokens.size()-1;i++)if(text(tokens,i).equals("as")&&identifier(text(tokens,i+1)))compositionAliases.add(text(tokens,i+1));
        for(int i=0;i<tokens.size()-1;i++)if(text(tokens,i).equals("on")) {
            int j=i+1;boolean operator=text(tokens,j).equals("op");if(operator)j++;
            String first=text(tokens,j++);if(!identifier(first)&&!operator)continue;
            var parts=new ArrayList<String>();var counts=new ArrayList<Integer>();
            parts.add(first);
            if(!text(tokens,j).equals(":")){counts.add(0);add(parts,counts);continue;}
            while(text(tokens,j).equals(":")) {
                j++;int count=1;
                if(text(tokens,j).equals("[")) {
                    j++;count=0;
                    while(j<tokens.size()&&!text(tokens,j).equals("]")) {
                        count++;j=skipParam(tokens,j);if(text(tokens,j).equals(","))j++;else break;
                    }
                    if(text(tokens,j).equals("]"))j++;
                } else if(text(tokens,j).equals("...")){count=-1;j+=2;}
                else j=skipParam(tokens,j);
                counts.add(count);
                if(text(tokens,j).equals(",")&&identifier(text(tokens,j+1))&&text(tokens,j+2).equals(":"))j++;
                if(identifier(text(tokens,j))&&text(tokens,j+1).equals(":")){parts.add(text(tokens,j));j++;}
                else break;
            }
            if(parts.size()==counts.size()) {
                add(parts,counts);
                if(parts.getFirst().equals("initialize"))for(String factory:List.of("new","create")) {
                    var factoryParts=new ArrayList<>(parts);factoryParts.set(0,factory);add(factoryParts,counts);
                }
            }
        }
    }
    private static int skipParam(List<? extends Token> t,int i) {
        if(i>=t.size())return i;
        i++;
        i=skipTypeAnnotation(t,i);
        return i;
    }
    private static int skipTypeAnnotation(List<? extends Token> t,int i){
        if(i>=t.size())return i;
        if(text(t,i).equals("^"))i++;
        int start=i;
        i=skipTypeCore(t,i);
        if(i==start)return i;
        if(text(t,i).equals("&")) {
            int right=skipTypeCore(t,i+1);
            if(right>i+1)i=right;
        }
        return i;
    }
    private static int skipTypeCore(List<? extends Token> t,int i){
        if(i>=t.size())return i;
        if(text(t,i).equals("&"))i++;
        if(!(identifier(text(t,i))||text(t,i).equals("nil")))return i;
        i++;
        if(text(t,i).equals("[")){int depth=0;do{String v=text(t,i++);if(v.equals("["))depth++;else if(v.equals("]"))depth--;}while(i<t.size()&&depth>0);}
        return i;
    }
    private static String text(List<? extends Token> t,int i){return i>=0&&i<t.size()?t.get(i).getText():"";}
    private static boolean identifier(String s){return s.matches("[A-Za-z_][A-Za-z_0-9]*\\??");}
    private static boolean operand(TokenStream in) {
        String t=in.LT(1).getText();
        if(t.equals("-")||t.equals("~~~"))return true;
        return identifier(t)&&!Set.of("end","else","elsif","otherwise","default","is","yield","do","in","then","to","by","and","or","isa","rescue","return","on","impl","record","abstract","data","sign","let","var","final","while","until","break","next").contains(t)||t.startsWith("@")&&!t.equals("@end")||t.startsWith("\"")||t.startsWith("~\"")||t.startsWith("\\\"")||t.startsWith("`")||t.startsWith("begin{")||t.matches("[0-9].*")||Set.of("(","[","#{","{","%[","%{","#","|","||","&attrs","&block","&it").contains(t);
    }
    public boolean knownImplicitStart(TokenStream in) {
        String name=in.LT(1).getText();
        return signatures.stream().anyMatch(s->s.parts().getFirst().equals(name)&&s.arities().getFirst()!=0)&&implicitStart(in);
    }
    public boolean implicitStart(TokenStream in) {
        String first=in.LT(1).getText();
        if(!identifier(first))return false;
        if(in.LT(2).getText().equals("[")&&in.LT(1).getStopIndex()+1==in.LT(2).getStartIndex())return false;
        if(in.LT(2).getText().equals(":"))return true;
        Set<Integer> options=arities.get(first);
        if(options!=null&&options.stream().noneMatch(n->n!=0))return false;
        if(options==null&&Set.of("if","for").contains(in.LT(2).getText()))return false;
        int index=in.index();in.seek(index+1);
        boolean result=operand(in);
        // A spaced negative operand is a call; n-1 remains subtraction.
        if(in.LT(1).getText().equals("-"))result=in.LT(-1).getStopIndex()+1<in.LT(1).getStartIndex()&&in.LT(1).getStopIndex()+1==in.LT(2).getStartIndex();
        in.seek(index);return result;
    }
    public boolean takesArguments(String selector,TokenStream in) {
        Set<Integer> options=arities.get(selector);
        if(options!=null&&options.stream().allMatch(n->n==0))return false;
        String next=in.LT(1).getText();
        if(Set.of("end","else","elsif","then","otherwise","default","is").contains(next))return false;
        if(options!=null&&options.contains(-1)&&arities.containsKey(next)&&Set.of("then","otherwise").contains(next))return false;
        return next.equals("do")||operand(in);
    }
    public boolean commaContinuation(List<String> parsedParts,TokenStream in) {
        if(!in.LT(1).getText().equals(","))return false;
        int look=2;while(in.LT(look).getText().equals("\n")||in.LT(look).getText().equals("\r\n"))look++;
        String next=in.LT(look).getText();
        for(var signature:signatures)for(int count=1;count<signature.parts().size()&&count<=parsedParts.size();count++)
            if(signature.parts().get(count).equals(next)&&signature.parts().subList(0,count).equals(parsedParts.subList(parsedParts.size()-count,parsedParts.size())))return true;
        return false;
    }
    public boolean positionalContinuation(String selector,TokenStream in) {
        if(!in.LT(1).getText().equals(","))return false;
        String next=in.LT(2).getText();
        if(in.LT(3).getText().equals(":"))return false;
        for(var signature:signatures)for(int i=0;i<signature.parts().size()-1;i++)if(signature.parts().get(i).equals(selector)&&signature.parts().get(i+1).equals(next))return false;
        return true;
    }
    public boolean sequence(List<String> parts) {return signatures.stream().anyMatch(s->s.parts().equals(parts));}
    public int longestPrefix(List<String> parts,int start) {
        int best=1;
        for(var s:signatures)if(start+s.parts().size()<=parts.size()&&s.parts().equals(parts.subList(start,start+s.parts().size())))best=Math.max(best,s.parts().size());
        return best;
    }
}
