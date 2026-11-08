package mach.frontend;
import mach.ast.Ast;
import mach.ast.Ast.*;
import mach.frontend.generated.*;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import java.util.*;

/** Builds a source AST; deliberately has no dependency on the bytecode backend. */
public final class Frontend extends MachOneBaseVisitor<Object> {
    private final String file;
    private final MessageSignatures signatures;
    private final Set<Expr> explicitlyGrouped=Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Expr> inlineDoBlocks=Collections.newSetFromMap(new IdentityHashMap<>());
    public Frontend(String file) { this(file,new MessageSignatures()); }
    public Frontend(String file,MessageSignatures signatures) { this.file=file;this.signatures=signatures; }
    public Ast.Program parse(String source) {
        var lexer=new MachOneLexer(CharStreams.fromString(source));
        var listener=new BaseErrorListener() {
            @Override public void syntaxError(Recognizer<?,?> r,Object offending,int line,int col,String msg,RecognitionException e) {
                throw new Diagnostic("M1001",new Span(file,line,col+1),msg);
            }
        };
        lexer.removeErrorListeners();lexer.addErrorListener(listener);
        var tokens=new CommonTokenStream(lexer);tokens.fill();signatures.collect(tokens.getTokens());tokens.seek(0);
        var parser=new MachOneParser(tokens);parser.setMessageSignatures(signatures);
        parser.removeErrorListeners();parser.addErrorListener(listener);
        return new Ast.Program(body(parser.program().body()));
    }
    private Span span(ParserRuleContext c) { return new Span(file,c.start.getLine(),c.start.getCharPositionInLine()+1); }
    private List<Stmt> body(MachOneParser.BodyContext c) { return c.statement().stream().map(s->(Stmt)visit(s)).toList(); }
    private Expr ex(ParseTree c) {return (Expr)visit(c);}
    private Type type(MachOneParser.TypeRefContext c) {
        if(c==null)return null;
        Type left=typeCore(c.typeCore(0));
        if(c.typeCore().size()==2)return new Type(left.name(),left.args(),TypeFlavor.BOUND_VARIABLE,typeCore(c.typeCore(1)));
        return left;
    }
    private Type typeCore(MachOneParser.TypeCoreContext c){
        Type atom=typeAtom(c.typeAtom());
        return c.getText().startsWith("&")?new Type(atom.name(),atom.args(),TypeFlavor.ABSTRACT_CONSTRAINT,null):atom;
    }
    private Type typeAtom(MachOneParser.TypeAtomContext c){
        var args=new ArrayList<Type>();for(var a:c.typeArgument())flattenTypes(a,args);
        return new Type(c.getChild(0).getText(),args);
    }
    private void flattenTypes(MachOneParser.TypeArgumentContext c,List<Type> output) {
        if(c.typeRef()!=null)output.add(type(c.typeRef()));else for(var child:c.typeArgument())flattenTypes(child,output);
    }
    private List<Type> signTypes(MachOneParser.SignItemContext c) {return c.typeRef().stream().map(this::type).toList();}
    @Override public Object visitModuleDecl(MachOneParser.ModuleDeclContext c) {return new Ast.Module(c.modulePath().getText(),span(c));}
    @Override public Object visitRequireDecl(MachOneParser.RequireDeclContext c) {
        var target=c.requireTarget();
        var items=new ArrayList<ImportItem>();
        if(target.requireSelection()!=null)for(var item:target.requireSelection().requireEntry()) {
            var ids=item.ID();
            var name=ids.getFirst().getText();
            var alias=ids.size()>1?ids.get(1).getText():null;
            items.add(new ImportItem(name,alias,span(item)));
        }
        String alias=null;var raw=target.getText();int index=raw.lastIndexOf(" as ");if(index>=0)alias=raw.substring(index+4).trim();
        return new Require(target.modulePath().getText(),items,alias,span(c));
    }
    @Override public Object visitBinding(MachOneParser.BindingContext c) {
        Type declared=type(c.typeRef());
        return new Bind(c.getChild(0).getText(),c.name().getText(),declared,false,ex(c.expr()),span(c));
    }
    @Override public Object visitAssignment(MachOneParser.AssignmentContext c) {return new Assign(c.name().getText(),c.getChild(1).getText(),ex(c.expr()),span(c));}
    @Override public Object visitRet(MachOneParser.RetContext c) {return new Return(c.expr()==null?nil(c):ex(c.expr()),span(c));}
    @Override public Object visitExpressionStmt(MachOneParser.ExpressionStmtContext c) {return new Eval(ex(c.expr()),span(c));}
    @Override public Object visitMethod(MachOneParser.MethodContext c) {
        var h=c.methodHead();var parts=new ArrayList<String>();var params=new ArrayList<Param>();
        var templateParts=new ArrayList<Expr>();
        if(h.operator()!=null)templateParts.add(new Literal(h.operator().getText(),new Type("str"),span(h)));else for(var selector:h.selector())templateParts.add(selectorName(selector));
        for(var part:templateParts)if(part instanceof Literal literal)parts.add(literal.value().toString());
        for(var group:h.parameterGroup()) {
            if(group.ID()!=null)params.add(new Param(group.ID().getText(),type(group.typeRef()),true,false));
            else params.addAll(group.parameter().stream().map(p->new Param(p.ID().getText(),type(p.typeRef()),false,false)).toList());
        }
        for(int i=0;i<params.size()-1;i++)if(params.get(i).variadic())throw new Diagnostic("M1006",span(c),"parâmetro variádico deve ser o último parâmetro");
        boolean guarded=c.getText().contains(",if");
        Expr guard=guarded?ex(c.expr(0)):null;
        List<Stmt> statements=c.body()!=null?body(c.body()):List.of(new Return(ex(c.expr(c.expr().size()-1)),span(c)));
        if(templateParts.stream().anyMatch(p->!(p instanceof Literal)))return new TemplateMethod(templateParts,params,type(c.typeRef()),guard,statements,span(c));
        return new Method(null,parts,params,type(c.typeRef()),guard,statements,span(c));
    }
    private Expr selectorName(MachOneParser.SelectorContext c) {
        return c.templateName()==null?new Literal(c.getText(),new Type("str"),span(c)):ex(c.templateName());
    }
    @Override public Object visitCompileFor(MachOneParser.CompileForContext c) {return new CompileFor(c.ID().getText(),ex(c.expr()),body(c.body()),span(c));}
    @Override public Object visitCompileIf(MachOneParser.CompileIfContext c) {
        var conditions=c.expr().stream().map(this::ex).toList();var arms=c.body().stream().map(this::body).toList();
        return new CompileIf(conditions,arms.subList(0,conditions.size()),arms.size()>conditions.size()?arms.getLast():List.of(),span(c));
    }
    @Override public Object visitTemplateName(MachOneParser.TemplateNameContext c) {
        var parts=new ArrayList<Expr>();
        for(var child:c.children) {
            if(child instanceof MachOneParser.SpliceContext splice)parts.add(ex(splice));
            else if(child instanceof TerminalNode t&&t.getSymbol().getType()==MachOneParser.ID)parts.add(new Literal(t.getText(),new Type("str"),span(c)));
        }
        return new TemplateName(parts,span(c));
    }
    @Override public Object visitSplice(MachOneParser.SpliceContext c) {return new Splice(ex(c.expr()),span(c));}
    @Override public Object visitSpliceExpr(MachOneParser.SpliceExprContext c) {return ex(c.splice());}
    @Override public Object visitTemplateExpr(MachOneParser.TemplateExprContext c) {
        var template=(TemplateName)ex(c.templateName());
        return template.parts().size()==1&&template.parts().getFirst() instanceof Splice?template.parts().getFirst():template;
    }
    @Override public Object visitImplDecl(MachOneParser.ImplDeclContext c) {return new Impl(type(c.typeRef()),c.body()==null?List.of((Stmt)visit(c.statement())):body(c.body()),span(c));}
    @Override public Object visitBoundedImplDecl(MachOneParser.BoundedImplDeclContext c) {return new BoundedImpl(c.ID().getText(),type(c.typeRef()),body(c.body()),span(c));}
    @Override public Object visitAbstractData(MachOneParser.AbstractDataContext c) {return new Abstract(c.ID().getText(),true,body(c.body()),span(c));}
    @Override public Object visitAbstractDispatch(MachOneParser.AbstractDispatchContext c) {return new Abstract(c.ID().getText(),false,body(c.body()),span(c));}
    @Override public Object visitAlias(MachOneParser.AliasContext c) {return new Alias(c.ID().getText(),type(c.typeRef()),span(c));}
    @Override public Object visitSignature(MachOneParser.SignatureContext c) {
        var types=new ArrayList<Type>();for(var item:c.signItem())types.addAll(signTypes(item));
        return new Sign(types,type(c.typeRef()),span(c));
    }
    @Override public Object visitRecordDecl(MachOneParser.RecordDeclContext c) {
        var ids=c.ID().stream().map(ParseTree::getText).toList();
        List<String> parameters=new ArrayList<>(),derives=new ArrayList<>();boolean inParams=false,inDerives=false;
        for(var ch:c.children) {
            if(ch.getText().equals("[")) inParams=true;
            else if(ch.getText().equals("]")) inParams=false;
            else if(ch.getText().equals("(")) inDerives=true;
            else if(ch.getText().equals(")")) inDerives=false;
            else if(ch instanceof TerminalNode t && t.getSymbol().getType()==MachOneParser.ID) {
                if(inParams)parameters.add(ch.getText());else if(inDerives)derives.add(ch.getText());
            }
        }
        var uses=new ArrayList<Use>();var fields=new ArrayList<Field>();
        for(var member:c.recordMember()) {
            if(member.field()==null){String alias=member.ID().getText();Type target=type(member.typeRef());uses.add(new Use(alias,target,span(member)));fields.add(new Field(alias,target,List.of("<composition>")));continue;}
            var f=member.field();var labels=new ArrayList<String>();var names=new ArrayList<String>();boolean block=false;
            for(var ch:f.children) {
                if(ch.getText().equals("{"))block=true;
                if(ch instanceof TerminalNode t&&t.getSymbol().getType()==MachOneParser.ID){if(block)labels.add(ch.getText());else names.add(ch.getText());}
            }
            var name=names.removeLast();labels.addAll(names);fields.add(new Field(name,type(f.typeRef()),List.copyOf(labels)));
        }
        return new Ast.Record(ids.getFirst(),parameters,type(c.typeRef()),derives,List.copyOf(fields),List.copyOf(uses),span(c));
    }
    @Override public Object visitConditional(MachOneParser.ConditionalContext c) {
        var conditions=c.expr().stream().map(this::ex).toList();var bodies=c.body().stream().map(this::body).toList();
        return new Branch(conditions,bodies.subList(0,conditions.size()),bodies.size()>conditions.size()?bodies.getLast():List.of(),span(c));
    }
    @Override public Object visitUnless(MachOneParser.UnlessContext c) {return new Branch(List.of(new Unary("not",ex(c.expr()),span(c))),List.of(body(c.body())),List.of(),span(c));}
    @Override public Object visitBeingLoop(MachOneParser.BeingLoopContext c) {
        var name=c.ID().getText();
        var initial=ex(c.expr(0));
        var condition=ex(c.expr(1));
        var loopBody=body(c.body());
        var test=c.getChild(2).getText().equals("until")?new Unary("not",condition,span(c)):condition;
        return new BeingLoop(name,initial,test,loopBody,span(c));
    }
    @Override public Object visitLoopStmt(MachOneParser.LoopStmtContext c) {
        var bodyStatements=c.body()!=null?body(c.body()):List.<Stmt>of();
        var condition=new Literal(true,new Type("bool"),span(c));
        return new While(condition,bodyStatements,span(c));
    }
    @Override public Object visitWhileLoop(MachOneParser.WhileLoopContext c) {
        Expr test=ex(c.expr());if(c.getChild(0).getText().equals("until"))test=new Unary("not",test,span(c));return new While(test,body(c.body()),span(c));
    }
    @Override public Object visitForLoop(MachOneParser.ForLoopContext c) {return new For(c.ID().stream().map(ParseTree::getText).toList(),ex(c.expr()),body(c.body()),span(c));}
    @Override public Object visitComprehensionExpr(MachOneParser.ComprehensionExprContext c) {
        var value=c.comprehensionValue();boolean pair=value.opExpr()!=null;
        Expr key=pair?ex(value.opExpr()):null;
        return new Comprehension(c.ID().stream().map(ParseTree::getText).toList(),ex(c.expr(0)),key,ex(value.expr()),c.expr().size()>1?ex(c.expr(1)):null,span(c));
    }
    @Override public Object visitBreakStmt(MachOneParser.BreakStmtContext c) {return new Jump(true,span(c));}
    @Override public Object visitNextStmt(MachOneParser.NextStmtContext c) {return new Jump(false,span(c));}
    private Literal nil(ParserRuleContext c) {return new Literal(null,new Type("nil"),span(c));}
    @Override public Object visitIntegerExpr(MachOneParser.IntegerExprContext c) {return LiteralDecoder.integer(c.getText(),span(c));}
    @Override public Object visitFloatExpr(MachOneParser.FloatExprContext c) {return LiteralDecoder.floating(c.getText(),span(c));}
    @Override public Object visitCharExpr(MachOneParser.CharExprContext c) {return LiteralDecoder.character(c.getText(),span(c));}
    @Override public Object visitRegexExpr(MachOneParser.RegexExprContext c) {return LiteralDecoder.regex(c.getText(),span(c));}
    @Override public Object visitRawStringExpr(MachOneParser.RawStringExprContext c) {return LiteralDecoder.rawString(c.getText(),span(c));}
    @Override public Object visitHeredocExpr(MachOneParser.HeredocExprContext c) {return LiteralDecoder.heredoc(c.getText(),span(c));}
    @Override public Object visitStringExpr(MachOneParser.StringExprContext c) {
        String text=c.getText();var literal=new StringBuilder();var segments=new ArrayList<Expr>();
        for(int i=1;i<text.length()-1;i++) {
            char ch=text.charAt(i);
            if(ch=='\\') {ch=text.charAt(++i);literal.append(switch(ch){case 'n'->'\n';case 'r'->'\r';case 't'->'\t';default->ch;});continue;}
            if(ch=='$'&&i+1<text.length()-1&&(Character.isLetter(text.charAt(i+1))||text.charAt(i+1)=='_'||text.charAt(i+1)=='(')) {
                segments.add(new Literal(literal.toString(),new Type("str"),span(c)));literal.setLength(0);
                String expression;
                if(text.charAt(i+1)=='(') {
                    int start=i+2,j=start,depth=1;
                    for(;j<text.length()-1;j++) {char v=text.charAt(j);if(v=='(')depth++;else if(v==')'&&--depth==0)break;}
                    if(depth!=0)throw new Diagnostic("M1003",span(c),"interpolação sem fechamento");
                    expression=text.substring(start,j);i=j;
                } else {
                    int start=i+1,j=start;
                    while(j<text.length()-1&&(Character.isLetterOrDigit(text.charAt(j))||text.charAt(j)=='_'))j++;
                    expression=text.substring(start,j);i=j-1;
                }
                Expr value=((Call)((Eval)new Frontend(file,signatures).parse("show "+expression).body().getFirst()).value()).args().getFirst();
                String op="stringify";
                if(i+3<text.length()&&text.charAt(i+1)==':'&&text.charAt(i+2)=='.'&&Character.isDigit(text.charAt(i+3))) {
                    int j=i+3;while(j<text.length()-1&&Character.isDigit(text.charAt(j)))j++;
                    op="format:"+text.substring(i+3,j);i=j-1;
                }
                segments.add(new Unary(op,value,span(c)));
            } else literal.append(ch);
        }
        segments.add(new Literal(literal.toString(),new Type("str"),span(c)));
        Expr result=segments.getFirst();for(int i=1;i<segments.size();i++)result=new Binary("+",result,segments.get(i),span(c));return result;
    }
    @Override public Object visitBoolExpr(MachOneParser.BoolExprContext c) {return new Literal(Boolean.parseBoolean(c.getText()),new Type("bool"),span(c));}
    @Override public Object visitNilExpr(MachOneParser.NilExprContext c) {return nil(c);}
    @Override public Object visitTagExpr(MachOneParser.TagExprContext c) {return c.ID()!=null||c.operator()!=null?new Literal(c.ID()!=null?c.ID().getText():c.operator().getText(),new Type("tag"),span(c)):new DynamicTag(((Splice)ex(c.splice())).value(),span(c));}
    @Override public Object visitNameExpr(MachOneParser.NameExprContext c) {return new Name(c.getText(),span(c));}
    @Override public Object visitParenExpr(MachOneParser.ParenExprContext c) {Expr result=ex(c.expr());explicitlyGrouped.add(result);return result;}
    private Unary unary(ParserRuleContext c) {return new Unary(c.getChild(0).getText(),ex(c.getChild(1)),span(c));}
    @Override public Object visitNegExpr(MachOneParser.NegExprContext c) {return unary(c);}
    @Override public Object visitBitNotExpr(MachOneParser.BitNotExprContext c) {return unary(c);}
    @Override public Object visitNotExpr(MachOneParser.NotExprContext c) {return unary(c);}
    private Binary binary(ParserRuleContext c) {return new Binary(c.getChild(1).getText(),ex(c.getChild(0)),ex(c.getChild(2)),span(c));}
    @Override public Object visitPowerExpr(MachOneParser.PowerExprContext c){return binary(c);}
    @Override public Object visitMulExpr(MachOneParser.MulExprContext c){return binary(c);}
    @Override public Object visitAddExpr(MachOneParser.AddExprContext c){return binary(c);}
    @Override public Object visitShiftExpr(MachOneParser.ShiftExprContext c){return binary(c);}
    @Override public Object visitBitAndExpr(MachOneParser.BitAndExprContext c){return binary(c);}
    @Override public Object visitBitXorExpr(MachOneParser.BitXorExprContext c){return binary(c);}
    @Override public Object visitBitOrExpr(MachOneParser.BitOrExprContext c){return binary(c);}
    @Override public Object visitCollectionExpr(MachOneParser.CollectionExprContext c){return binary(c);}
    @Override public Object visitCompareExpr(MachOneParser.CompareExprContext c){return binary(c);}
    @Override public Object visitEqualExpr(MachOneParser.EqualExprContext c){return binary(c);}
    @Override public Object visitAndExpr(MachOneParser.AndExprContext c){return binary(c);}
    @Override public Object visitOrExpr(MachOneParser.OrExprContext c){return binary(c);}
    @Override public Object visitElvisExpr(MachOneParser.ElvisExprContext c){return binary(c);}
    @Override public Object visitRangeExpr(MachOneParser.RangeExprContext c) {return new Range(ex(c.opExpr(0)),ex(c.opExpr(1)),c.opExpr().size()==3?ex(c.opExpr(2)):new Literal(1L,new Type("int"),span(c)),c.getChild(1).getText().equals(".."),span(c));}
    @Override public Object visitObjectAccessExpr(MachOneParser.ObjectAccessExprContext c) {
        return new Ast.ObjectAccess(ex(c.opExpr(0)),ex(c.opExpr(1)),span(c));
    }
    @Override public Object visitVectorAccessExpr(MachOneParser.VectorAccessExprContext c) {
        return new Ast.VectorAccess(ex(c.indexExpr()),ex(c.opExpr()),span(c));
    }
    @Override public Object visitFieldAccessExpr(MachOneParser.FieldAccessExprContext c) {
        var key=c.fieldKey();Expr value;
        if(key.getChild(0).getText().equals("["))throw new Diagnostic("M1004",span(c),"operador 's aceita apenas seletor simples; use of para caminhos");
        else if(key.ID()!=null)value=new Literal(key.ID().getText(),new Type("tag"),span(key));
        else if(key.splice()!=null)value=new DynamicTag(((Splice)ex(key.splice())).value(),span(key));
        else if(key.name()!=null)value=new Name(key.name().getText(),span(key));else value=ex(key.expr(0));
        return new Ast.ObjectAccess(value,ex(c.opExpr()),span(c));
    }
    @Override public Object visitIndexExpr(MachOneParser.IndexExprContext c) {
        if(c.indexAtom().isEmpty())return new Ast.Vector(c.opExpr().stream().map(this::ex).toList(),span(c));
        Expr first=ex(c.indexAtom(0));if(c.indexAtom().size()==1)return first;
        return new Range(first,ex(c.indexAtom(1)),c.indexAtom().size()>2?ex(c.indexAtom(2)):new Literal(1L,new Type("int"),span(c)),c.getChild(1).getText().equals(".."),span(c));
    }
    @Override public Object visitIndexAtom(MachOneParser.IndexAtomContext c) {
        if(c.expr()!=null)return ex(c.expr());Expr value=c.INT()!=null?LiteralDecoder.integer(c.INT().getText(),span(c)):new Name(c.name().getText(),span(c));
        return c.getChild(0).getText().equals("-")?new Unary("-",value,span(c)):value;
    }
    @Override public Object visitDeletionExpr(MachOneParser.DeletionExprContext c) {return new Call(ex(c.opExpr()),List.of("\\\\"),c.deletionArgs().opExpr().stream().map(this::ex).toList(),span(c));}
    @Override public Object visitObjectExpr(MachOneParser.ObjectExprContext c) {
        var keys=new ArrayList<Expr>();
        for(var key:c.objectKey()) {
            if(c.getChild(0).getText().equals("{")&&!key.getText().startsWith("#"))throw new Diagnostic("M1004",span(c),"chave literal em { } exige #tag");
            if(key.splice()!=null)keys.add(new DynamicTag(((Splice)ex(key.splice())).value(),span(key)));
            else if(key.templateName()!=null)keys.add(ex(key.templateName()));
            else keys.add(new Literal(key.ID().getText(),new Type("tag"),span(key)));
        }
        var values=c.expr().stream().map(this::ex).toList();
        if(keys.stream().anyMatch(k->!(k instanceof Literal)))return new DynamicObject(keys,values,span(c));
        return new ObjectLiteral(keys.stream().map(k->((Literal)k).value().toString()).toList(),values,span(c));
    }
    @Override public Object visitConversionExpr(MachOneParser.ConversionExprContext c) {return new Convert(ex(c.opExpr()),type(c.typeRef()),ConversionMode.FORCED,span(c));}
    @Override public Object visitRescueExpr(MachOneParser.RescueExprContext c) {return new Rescue(ex(c.opExpr(0)),ex(c.opExpr(1)),span(c));}
    @Override public Object visitTernaryExpr(MachOneParser.TernaryExprContext c) {return new Choice(ex(c.expr(0)),ex(c.expr(1)),ex(c.expr(2)),span(c));}
    @Override public Object visitLambdaExpr(MachOneParser.LambdaExprContext c) {
        return new Lambda(c.parameter().stream().map(p->new Param(p.ID().getText(),type(p.typeRef()),false,false)).toList(),type(c.typeRef()),body(c.body()),span(c));
    }
    @Override public Object visitCompactVectorExpr(MachOneParser.CompactVectorExprContext c) {return new Ast.CompactVector(c.opExpr().stream().map(this::ex).toList(),span(c));}
    @Override public Object visitCompactObjectExpr(MachOneParser.CompactObjectExprContext c) {
        var keys=new ArrayList<Expr>();
        for(var key:c.objectKey()) {
            if(key.splice()!=null)keys.add(new DynamicTag(((Splice)ex(key.splice())).value(),span(key)));
            else if(key.templateName()!=null)keys.add(ex(key.templateName()));else keys.add(new Literal(key.ID().getText(),new Type("tag"),span(key)));
        }
        var values=c.opExpr().stream().map(this::ex).toList();
        if(keys.stream().anyMatch(k->!(k instanceof Literal)))return new DynamicObject(keys,values,span(c));
        return new ObjectLiteral(keys.stream().map(k->((Literal)k).value().toString()).toList(),values,span(c));
    }
    @Override public Object visitVectorExpr(MachOneParser.VectorExprContext c) {return new Ast.Vector(c.expr().stream().map(this::ex).toList(),span(c));}
    @Override public Object visitIfValueExpr(MachOneParser.IfValueExprContext c) {
        Expr fallback=c.body().size()>c.expr().size()?new BlockExpr(body(c.body(c.body().size()-1)),span(c)):nil(c);
        for(int i=c.expr().size()-1;i>=0;i--)fallback=new Choice(ex(c.expr(i)),new BlockExpr(body(c.body(i)),span(c)),fallback,span(c));
        return fallback;
    }
    @Override public Object visitMatchExpr(MachOneParser.MatchExprContext c) {
        var arms=new ArrayList<MatchArm>();
        for(var a:c.matchArm())arms.add(new MatchArm(a.opExpr().stream().map(this::ex).toList(),a.expr()==null?null:ex(a.expr()),armValue(a.armValue()),span(a)));
        return new Match(ex(c.expr()),arms,c.armValue()==null?null:armValue(c.armValue()),span(c));
    }
    private Expr armValue(MachOneParser.ArmValueContext c) {return c.body()==null?ex(c.expr()):new BlockExpr(body(c.body()),span(c));}
    @Override public Object visitDoExpr(MachOneParser.DoExprContext c) {
        if(c.body().size()>1)return new ChainedDo(body(c.body(0)),body(c.body(1)),span(c));
        var value=new Lambda(List.of(),null,body(c.body(0)),true,span(c));inlineDoBlocks.add(value);return value;
    }
    @Override public Object visitBlockExpr(MachOneParser.BlockExprContext c) {return new BlockExpr(body(c.body()),span(c));}
    @Override public Object visitTypeAppExpr(MachOneParser.TypeAppExprContext c) {
        var arguments=new ArrayList<Type>();for(var a:c.typeArgument())flattenTypes(a,arguments);
        return new TypeExpr(new Type(c.ID().getText(),arguments),span(c));
    }
    @Override public Object visitTypeTestExpr(MachOneParser.TypeTestExprContext c) {return new TypeTest(c.getChild(1).getText(),ex(c.opExpr()),type(c.typeRef()),span(c));}
    private Expr blockValue(Expr value) {
        if(value instanceof Lambda lambda&&inlineDoBlocks.contains(value))return new BlockExpr(lambda.body(),lambda.span());
        return value;
    }
    private Expr message(Expr receiver,MachOneParser.MessageContext c,Span location) {
        var parts=new ArrayList<String>();var arguments=new ArrayList<List<Expr>>();
        for(var part:c.messagePart()) {
            String selector=part.partName;parts.add(selector);
            var args=part.arguments()==null?List.<Expr>of():part.arguments().messageArgument().stream().map(this::ex).toList();
            if(args.size()==1&&args.getFirst() instanceof Ast.Vector vector&&signatures.groupedPositionals(selector,vector.items().size()))args=vector.items();
            if(args.size()==1&&args.getFirst() instanceof Range range&&!range.inclusive()&&!explicitlyGrouped.contains(range)&&signatures.sequence(List.of(selector,"to"))&&range.step() instanceof Literal step&&Objects.equals(step.value(),1L)) {
                arguments.add(List.of(range.start()));parts.add("to");arguments.add(List.of(range.end()));
            } else arguments.add(args);
        }
        Expr result=receiver;
        for(int i=0;i<parts.size();) {
            int count=signatures.longestPrefix(parts,i);var labels=parts.subList(i,i+count);var args=new ArrayList<Expr>();
            for(int j=i;j<i+count;j++)args.addAll(arguments.get(j));
            boolean conditional=result!=null&&(labels.equals(List.of("then"))||labels.equals(List.of("otherwise"))||labels.equals(List.of("then","otherwise")));
            if(conditional&&labels.equals(List.of("then"))&&args.size()==1&&args.getFirst() instanceof ChainedDo chain) {
                labels=List.of("then","otherwise");args=new ArrayList<>(List.of(new BlockExpr(chain.yes(),chain.span()),new BlockExpr(chain.no(),chain.span())));
            }
            for(int j=0;j<args.size();j++) {
                Expr arg=args.get(j);if(conditional)arg=blockValue(arg);
                if(arg instanceof BlockExpr block) {
                    var statements=new ArrayList<Stmt>();
                    if(conditional)statements.add(new Eval(new Unary("acknowledge:"+(labels.get(Math.min(j,labels.size()-1)).equals("then")?"true":"false"),result,location),location));
                    statements.addAll(block.body());arg=new Lambda(conditional?List.of():List.of(new Param("&it",null,false,false)),null,List.copyOf(statements),true,block.span());
                }
                args.set(j,arg);
            }
            if(result instanceof Name alias&&labels.equals(List.of("has"))&&signatures.compositionAlias(alias.name()))result=new ComposedArg(alias.name(),List.copyOf(args),location);
            else {
                Call trailing=result!=null&&!args.isEmpty()&&args.getLast() instanceof Call call&&!explicitlyGrouped.contains(call)&&Set.of("then","otherwise","then:otherwise").contains(call.selector())?call:null;
                if(trailing!=null)args.set(args.size()-1,trailing.receiver());
                result=new Call(result,List.copyOf(labels),args,location);if(trailing!=null)result=rebindConditional(trailing,result);
            }
            i+=count;
        }
        if(c.anonBlock()!=null) {
            if(!(result instanceof Call))throw new Diagnostic("M1007",location,"bloco anônimo exige envio de mensagem");
            var call=(Call)result;result=new Call(call.receiver(),call.parts(),call.args(),ex(c.anonBlock().opExpr()),location);
        }
        return result;
    }
    private Call rebindConditional(Call conditional,Expr receiver) {
        var arguments=new ArrayList<Expr>();
        for(Expr argument:conditional.args()) {
            if(argument instanceof Lambda lambda&&!lambda.body().isEmpty()&&lambda.body().getFirst() instanceof Eval eval&&eval.value() instanceof Unary unary&&unary.op().startsWith("acknowledge:")) {
                var body=new ArrayList<Stmt>(lambda.body());body.set(0,new Eval(new Unary(unary.op(),receiver,unary.span()),eval.span()));
                argument=new Lambda(lambda.params(),lambda.result(),List.copyOf(body),lambda.nonLocal(),lambda.span());
            }
            arguments.add(argument);
        }
        return new Call(receiver,conditional.parts(),List.copyOf(arguments),conditional.block(),conditional.span());
    }
    @Override public Object visitSendExpr(MachOneParser.SendExprContext c) {return message(ex(c.expr()),c.message(),span(c));}
    @Override public Object visitImplicitSend(MachOneParser.ImplicitSendContext c) {return message(null,c.message(),span(c));}
}
