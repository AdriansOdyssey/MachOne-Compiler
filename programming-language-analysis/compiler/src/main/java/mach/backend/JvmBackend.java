package mach.backend;
import mach.ast.Ast;
import mach.ast.Ast.*;
import mach.ast.Ast.Type;
import mach.ir.MachIR;
import org.objectweb.asm.*;
import java.util.*;
import static org.objectweb.asm.Opcodes.*;

/** Emits executable JVM instructions from typed MachIR. Never emits Java source. */
public final class JvmBackend {
    private static final String R="mach/runtime/RuntimeSupport",C=R+"$Context",F=R+"$Frame",OBJ="Ljava/lang/Object;",CTX="L"+C+";",FRAME="L"+F+";",ARR="[Ljava/lang/Object;";
    private static final String ITER="mach/runtime/IterationSupport";
    private static final String METHOD="("+CTX+OBJ+ARR+")"+OBJ;
    private static final String CONTROL="mach/runtime/NonLocalControl",TARGET="L"+CONTROL+"$Target;";
    private boolean nonLocalBody;
    private int returnTarget=-1;
    private Label returnRegionStart;
    private final Map<Lambda,Integer> lambdas=new IdentityHashMap<>();
    private MachIR ir;private ClassWriter writer;private String owner;
    private MethodVisitor mv;private int nextLocal,frame;
    private final Deque<Label> breaks=new ArrayDeque<>(),continues=new ArrayDeque<>();
    public byte[] generate(MachIR program,String className) {
        ir=program;owner=className.replace('.','/');writer=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);
        writer.visit(V21,ACC_PUBLIC|ACC_FINAL,owner,null,"java/lang/Object",null);
        writer.visitSource("MachOne",null);
        for(var e:ir.types().keySet())if(e instanceof Lambda l)lambdas.put(l,lambdas.size());
        for(var entry:lambdas.entrySet()) {
            var l=entry.getKey();begin("lambda$"+entry.getValue(),METHOD);
            mv.visitVarInsn(ALOAD,1);invoke(R,"capture","("+OBJ+")"+FRAME,true);mv.visitVarInsn(ASTORE,frame);
            for(int j=0;j<l.params().size();j++) {loadFrame();mv.visitLdcInsn(l.params().get(j).name());mv.visitVarInsn(ALOAD,2);push(j);mv.visitInsn(AALOAD);invoke(F,"bind","(Ljava/lang/String;"+OBJ+")V",false);}
            nonLocalBody=l.nonLocal();
            if(!nonLocalBody)openReturnRegion();
            block(l.body(),true);finishReturnRegion();end();
        }
        for(int i=0;i<ir.methods().size();i++) {
            var m=ir.methods().get(i);begin("method$"+i,METHOD);initFrame(false);
            if(m.receiver()!=null) {loadFrame();mv.visitLdcInsn("self");mv.visitVarInsn(ALOAD,1);invoke(F,"bind","(Ljava/lang/String;"+OBJ+")V",false);}
            bindParameters(m.params());
            openReturnRegion();block(m.body(),true);finishReturnRegion();end();
            if(m.guard()!=null) {
                begin("guard$"+i,METHOD);initFrame(false);
                if(m.receiver()!=null) {loadFrame();mv.visitLdcInsn("self");mv.visitVarInsn(ALOAD,1);invoke(F,"bind","(Ljava/lang/String;"+OBJ+")V",false);}
                bindParameters(m.params());
                openReturnRegion();expression(m.guard());finishReturnRegion();end();
            }
        }
        begin("execute","("+CTX+")"+OBJ);initFrame(true);initialize();block(ir.main(),true);mv.visitVarInsn(ALOAD,0);invoke(R,"finish","("+CTX+")V",true);mv.visitInsn(ARETURN);end();
        begin("main","([Ljava/lang/String;)V");
        mv.visitTypeInsn(NEW,C);mv.visitInsn(DUP);invoke(C,"<init>","()V",false);mv.visitVarInsn(ASTORE,1);
        mv.visitVarInsn(ALOAD,1);mv.visitVarInsn(ALOAD,0);invoke("mach/runtime/SystemLibrary","arguments","("+CTX+"[Ljava/lang/String;)V",true);
        mv.visitVarInsn(ALOAD,1);invoke(owner,"execute","("+CTX+")"+OBJ,true);mv.visitInsn(POP);mv.visitInsn(RETURN);end();
        writer.visitEnd();return writer.toByteArray();
    }
    private void begin(String name,String descriptor) {mv=writer.visitMethod(ACC_PUBLIC|ACC_STATIC,name,descriptor,null,null);mv.visitCode();nextLocal=4;frame=3;breaks.clear();continues.clear();returnTarget=-1;returnRegionStart=null;nonLocalBody=false;}
    private void openReturnRegion() {
        returnTarget=local();loadFrame();invoke(CONTROL,"open","("+FRAME+")"+TARGET,true);mv.visitVarInsn(ASTORE,returnTarget);
        returnRegionStart=new Label();mv.visitLabel(returnRegionStart);
    }
    private void closeReturnTarget() {if(returnTarget>=0){mv.visitVarInsn(ALOAD,returnTarget);invoke(CONTROL,"close","("+TARGET+")V",true);}}
    private void finishReturnRegion() {
        if(returnTarget<0){mv.visitInsn(ARETURN);return;}
        Label regionEnd=new Label(),signalHandler=new Label(),exceptionHandler=new Label();
        mv.visitLabel(regionEnd);closeReturnTarget();mv.visitInsn(ARETURN);
        mv.visitTryCatchBlock(returnRegionStart,regionEnd,signalHandler,CONTROL+"$Signal");
        mv.visitTryCatchBlock(returnRegionStart,regionEnd,exceptionHandler,null);
        int failure=local();mv.visitLabel(signalHandler);mv.visitVarInsn(ASTORE,failure);closeReturnTarget();
        mv.visitVarInsn(ALOAD,returnTarget);mv.visitVarInsn(ALOAD,failure);invoke(CONTROL,"receive","("+TARGET+"L"+CONTROL+"$Signal;)"+OBJ,true);mv.visitInsn(ARETURN);
        mv.visitLabel(exceptionHandler);mv.visitVarInsn(ASTORE,failure);closeReturnTarget();mv.visitVarInsn(ALOAD,failure);mv.visitInsn(ATHROW);
    }
    private void emitReturn(Expr value) {
        if(nonLocalBody){loadFrame();expression(value);invoke(CONTROL,"raise","("+FRAME+OBJ+")Ljava/lang/RuntimeException;",true);mv.visitInsn(ATHROW);}
        else {expression(value);closeReturnTarget();mv.visitInsn(ARETURN);}
    }
    private void end() {mv.visitMaxs(0,0);mv.visitEnd();}
    private int local() {return nextLocal++;}
    private void invoke(String owner,String name,String descriptor,boolean statik) {mv.visitMethodInsn(statik?INVOKESTATIC:name.equals("<init>")?INVOKESPECIAL:INVOKEVIRTUAL,owner,name,descriptor,false);}
    private void push(int value) {mv.visitLdcInsn(value);}
    private void loadFrame() {mv.visitVarInsn(ALOAD,frame);}
    private void initFrame(boolean global) {
        mv.visitVarInsn(ALOAD,0);invoke(R,"globals","("+CTX+")"+FRAME,true);
        if(!global)invoke(R,"child","("+FRAME+")"+FRAME,true);
        mv.visitVarInsn(ASTORE,frame);
        if(!global){loadFrame();mv.visitLdcInsn("&block");mv.visitVarInsn(ALOAD,0);invoke(R,"currentBlock","("+CTX+")"+OBJ,true);invoke(F,"bind","(Ljava/lang/String;"+OBJ+")V",false);}
    }
    private void bindParameters(List<Param> params) {
        for(int index=0;index<params.size();index++) {
            Param param=params.get(index);loadFrame();mv.visitLdcInsn(param.name());mv.visitVarInsn(ALOAD,2);push(index);
            if(param.variadic())invoke(ITER,"tail","("+ARR+"I)"+OBJ,true);else mv.visitInsn(AALOAD);
            invoke(F,"bind","(Ljava/lang/String;"+OBJ+")V",false);
        }
    }
    private void strings(List<String> values) {
        push(values.size());mv.visitTypeInsn(ANEWARRAY,"java/lang/String");
        for(int i=0;i<values.size();i++){mv.visitInsn(DUP);push(i);mv.visitLdcInsn(values.get(i));mv.visitInsn(AASTORE);}
    }
    private void array(List<Expr> values) {
        push(values.size());mv.visitTypeInsn(ANEWARRAY,"java/lang/Object");
        for(int i=0;i<values.size();i++) {mv.visitInsn(DUP);push(i);expression(values.get(i));mv.visitInsn(AASTORE);}
    }
    private String resolved(Type t) {
        if(t==null)return "any";
        if(ir.aliases().containsKey(t.name()))return resolved(ir.aliases().get(t.name()));
        return t.args().isEmpty()?t.name():t.name()+"["+String.join(",",t.args().stream().map(this::resolved).toList())+"]";
    }
    private String runtimeType(Type type) {
        if(type!=null&&type.flavor()==TypeFlavor.ABSTRACT_CONSTRAINT)return runtimeType(new Type(type.name(),type.args()));
        if(type!=null&&type.flavor()==TypeFlavor.BOUND_VARIABLE){
            String token="$"+type.name();
            if(type.bound()!=null)return token+"&"+runtimeType(type.bound());
            return token;
        }
        if(type!=null&&type.name().matches("[A-Z]")&&ir.records().stream().noneMatch(r->r.name().equals(type.name())||(r.parent()!=null&&r.parent().name().equals(type.name()))))return "$"+type.name();
        if(type!=null&&!type.args().isEmpty())return type.name()+"["+String.join(",",type.args().stream().map(this::runtimeType).toList())+"]";
        return resolved(type);
    }
    private void initialize() {
        for(var r:ir.records()) {
            mv.visitVarInsn(ALOAD,0);mv.visitLdcInsn(r.name());mv.visitLdcInsn(r.parent()==null?"":r.parent().name());
            strings(r.parameters());
            strings(r.fields().stream().map(f->f.name().startsWith("_")?f.name().substring(1):f.name()).toList());
            strings(r.fields().stream().map(f->resolved(f.type())).toList());
            strings(r.fields().stream().map(f->String.join(",",f.labels())+(f.name().startsWith("_")?",<private>":"")).toList());strings(r.derives());
            invoke(R,"defineRecord","("+CTX+"Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/String;)V",true);
        }
        for(int i=0;i<ir.methods().size();i++) {
            var m=ir.methods().get(i);mv.visitVarInsn(ALOAD,0);mv.visitLdcInsn(org.objectweb.asm.Type.getObjectType(owner));
            mv.visitLdcInsn("method$"+i);mv.visitLdcInsn(m.guard()==null?"":"guard$"+i);mv.visitLdcInsn(m.receiver()==null?"":resolved(m.receiver()));mv.visitLdcInsn(m.selector());
            strings(m.params().stream().map(p->{
                String type=runtimeType(p.type()!=null&&p.type().name().equals("vec")?p.type().args().getFirst():p.type());
                if(p.converting())type="^"+type;
                return p.variadic()?"..."+type:type;
            }).toList());
            invoke(R,"register","("+CTX+"Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;)V",true);
        }
    }
    private void scoped(List<Stmt> body) {
        int previous=frame,inner=local();loadFrame();invoke(R,"child","("+FRAME+")"+FRAME,true);mv.visitVarInsn(ASTORE,inner);frame=inner;
        block(body,false);frame=previous;
    }
    private void block(List<Stmt> statements,boolean result) {
        if(statements.isEmpty()) {if(result)mv.visitInsn(ACONST_NULL);return;}
        for(int i=0;i<statements.size();i++) {
            Stmt s=statements.get(i);boolean last=result&&i==statements.size()-1;
            mv.visitLabel(new Label());mv.visitLineNumber(Math.max(1,s.span().line()),labelAtCurrent());
            if(s instanceof Eval e) {expression(e.value());if(!last)mv.visitInsn(POP);}
            else if(s instanceof Return r) {emitReturn(r.value());if(last)mv.visitInsn(ACONST_NULL);}
            else {
                statement(s);if(last)mv.visitInsn(ACONST_NULL);
            }
        }
    }
    private Label labelAtCurrent() {var label=new Label();mv.visitLabel(label);return label;}
    private void statement(Stmt s) {
        if(s instanceof Bind b) {loadFrame();mv.visitLdcInsn(b.name());expression(b.value());invoke(F,"bind","(Ljava/lang/String;"+OBJ+")V",false);}
        else if(s instanceof Assign a) {
            if(a.name().equals("self")) {
                loadFrame();mv.visitLdcInsn("self");invoke(F,"get","(Ljava/lang/String;)"+OBJ,false);expression(a.value());invoke(R,"replaceSelf","("+OBJ+OBJ+")V",true);return;
            }
            loadFrame();mv.visitLdcInsn(a.name());
            if(a.op().equals("="))expression(a.value());
            else {mv.visitVarInsn(ALOAD,0);mv.visitLdcInsn(a.op().substring(0,1));loadFrame();mv.visitLdcInsn(a.name());invoke(F,"get","(Ljava/lang/String;)"+OBJ,false);expression(a.value());invoke(R,"binary","("+CTX+"Ljava/lang/String;"+OBJ+OBJ+")"+OBJ,true);}
            invoke(F,"set","(Ljava/lang/String;"+OBJ+")V",false);
        } else if(s instanceof Branch b) {
            Label end=new Label();
            for(int i=0;i<b.conditions().size();i++) {Label next=new Label();expression(b.conditions().get(i));truth();mv.visitJumpInsn(IFEQ,next);acknowledge(b.conditions().get(i),true);scoped(b.arms().get(i));mv.visitJumpInsn(GOTO,end);mv.visitLabel(next);}
            if(b.conditions().size()==1&&!b.otherwise().isEmpty())acknowledge(b.conditions().getFirst(),false);scoped(b.otherwise());mv.visitLabel(end);
        } else if(s instanceof BeingLoop loop) {
            loadFrame();mv.visitLdcInsn(loop.name());expression(loop.initial());invoke(F,"bind","(Ljava/lang/String;"+OBJ+")V",false);
            Label test=new Label(),end=new Label();breaks.push(end);continues.push(test);
            mv.visitLabel(test);expression(loop.condition());truth();mv.visitJumpInsn(IFEQ,end);block(loop.body(),false);mv.visitJumpInsn(GOTO,test);mv.visitLabel(end);breaks.pop();continues.pop();
        } else if(s instanceof While w) {
            Label test=new Label(),end=new Label();breaks.push(end);continues.push(test);
            mv.visitLabel(test);expression(w.condition());truth();mv.visitJumpInsn(IFEQ,end);scoped(w.body());mv.visitJumpInsn(GOTO,test);mv.visitLabel(end);breaks.pop();continues.pop();
        } else if(s instanceof For f) {
            int iter=local();expression(f.values());push(f.names().size());invoke(ITER,"iterator","("+OBJ+"I)Ljava/util/Iterator;",true);mv.visitVarInsn(ASTORE,iter);
            Label test=new Label(),end=new Label();breaks.push(end);continues.push(test);mv.visitLabel(test);
            mv.visitVarInsn(ALOAD,iter);mv.visitMethodInsn(INVOKEINTERFACE,"java/util/Iterator","hasNext","()Z",true);mv.visitJumpInsn(IFEQ,end);
            int previous=frame,inner=local();loadFrame();invoke(R,"child","("+FRAME+")"+FRAME,true);mv.visitVarInsn(ASTORE,inner);frame=inner;
            bindIteration(f.names(),iter);block(f.body(),false);frame=previous;mv.visitJumpInsn(GOTO,test);mv.visitLabel(end);breaks.pop();continues.pop();
        } else if(s instanceof Jump j)mv.visitJumpInsn(GOTO,j.isBreak()?breaks.peek():continues.peek());
        else if(!(s instanceof Sign))throw new IllegalStateException("unlowered statement: "+s);
    }
    private void bindIteration(List<String> names,int iterator) {
        int row=local();mv.visitVarInsn(ALOAD,iterator);mv.visitMethodInsn(INVOKEINTERFACE,"java/util/Iterator","next","()"+OBJ,true);mv.visitTypeInsn(CHECKCAST,ARR);mv.visitVarInsn(ASTORE,row);
        for(int index=0;index<names.size();index++){loadFrame();mv.visitLdcInsn(names.get(index));mv.visitVarInsn(ALOAD,row);push(index);mv.visitInsn(AALOAD);invoke(F,"bind","(Ljava/lang/String;"+OBJ+")V",false);}
    }
    private void comprehension(Comprehension value) {
        int builder=local();push(value.key()!=null?1:0);invoke(ITER,"builder","(Z)"+OBJ,true);mv.visitVarInsn(ASTORE,builder);
        int iter=local();expression(value.values());push(value.names().size());invoke(ITER,"iterator","("+OBJ+"I)Ljava/util/Iterator;",true);mv.visitVarInsn(ASTORE,iter);
        Label test=new Label(),end=new Label(),skip=new Label();mv.visitLabel(test);mv.visitVarInsn(ALOAD,iter);mv.visitMethodInsn(INVOKEINTERFACE,"java/util/Iterator","hasNext","()Z",true);mv.visitJumpInsn(IFEQ,end);
        int previous=frame,inner=local();loadFrame();invoke(R,"child","("+FRAME+")"+FRAME,true);mv.visitVarInsn(ASTORE,inner);frame=inner;bindIteration(value.names(),iter);
        if(value.filter()!=null){expression(value.filter());truth();mv.visitJumpInsn(IFEQ,skip);}
        mv.visitVarInsn(ALOAD,builder);if(value.key()!=null)expression(value.key());expression(value.value());
        invoke(ITER,value.key()!=null?"put":"add",value.key()!=null?"("+OBJ+OBJ+OBJ+")V":"("+OBJ+OBJ+")V",true);
        frame=previous;mv.visitLabel(skip);mv.visitJumpInsn(GOTO,test);mv.visitLabel(end);mv.visitVarInsn(ALOAD,builder);invoke(ITER,"finish","("+OBJ+")"+OBJ,true);
    }
    private void truth() {invoke(R,"truth","("+OBJ+")Z",true);}
    private void bool(boolean value) {mv.visitFieldInsn(GETSTATIC,"java/lang/Boolean",value?"TRUE":"FALSE","Ljava/lang/Boolean;");}
    private void expression(Expr e) {
        if(ir.operatorCalls().contains(e)) {
            if(e instanceof Unary unary)call(new Call(unary.value(),List.of(unary.op()),List.of(),unary.span()));
            else {var binary=(Binary)e;call(new Call(binary.left(),List.of(binary.op()),List.of(binary.right()),binary.span()));}
            return;
        }
        if(e instanceof Literal l) {
            if(l.value()==null)mv.visitInsn(ACONST_NULL);
            else if(l.value() instanceof Number number&&Set.of("float","double").contains(ir.types().getOrDefault(l,l.type()).name())) {mv.visitLdcInsn(number.doubleValue());mv.visitLdcInsn(ir.types().getOrDefault(l,l.type()).name());invoke(R,"floating","(DLjava/lang/String;)"+OBJ,true);}
            else if(l.value() instanceof Long n) {mv.visitLdcInsn(n);mv.visitLdcInsn(ir.types().getOrDefault(l,l.type()).name());invoke(R,"integer","(JLjava/lang/String;)"+OBJ,true);}
            else if(l.value() instanceof java.math.BigInteger n) {mv.visitLdcInsn(n.toString());mv.visitLdcInsn(ir.types().getOrDefault(l,l.type()).name());invoke(R,"integerText","(Ljava/lang/String;Ljava/lang/String;)"+OBJ,true);}
            else if(l.value() instanceof Double n) {mv.visitLdcInsn(n);mv.visitLdcInsn(ir.types().getOrDefault(l,l.type()).name());invoke(R,"floating","(DLjava/lang/String;)"+OBJ,true);}
            else if(l.value() instanceof Boolean b)bool(b);
            else {mv.visitLdcInsn(l.value().toString());if(l.type().name().equals("tag"))invoke(R,"tag","(Ljava/lang/String;)"+OBJ,true);else if(l.type().name().equals("char"))invoke(R,"character","(Ljava/lang/String;)"+OBJ,true);else if(l.type().name().equals("regex"))invoke(R,"regex","(Ljava/lang/String;)"+OBJ,true);}
        } else if(e instanceof Name n) {
            var type=ir.types().get(n);
            if(n.name().equals("&attrs")) {loadFrame();mv.visitLdcInsn("self");invoke(F,"get","(Ljava/lang/String;)"+OBJ,false);invoke(R,"attrs","("+OBJ+")"+OBJ,true);}
            else if(ir.typeNames().contains(n)) {mv.visitLdcInsn(type.args().getFirst().toString());invoke(R,"type","(Ljava/lang/String;)"+OBJ,true);}
            else if(ir.implicitCalls().contains(n))call(new Call(null,List.of(n.name()),List.of(),n.span()));
            else {loadFrame();mv.visitLdcInsn(n.name());invoke(F,"get","(Ljava/lang/String;)"+OBJ,false);}
        } else if(e instanceof TypeExpr t) {mv.visitLdcInsn(t.type().toString());invoke(R,"type","(Ljava/lang/String;)"+OBJ,true);}
        else if(e instanceof BlockExpr block) {int previous=frame,inner=local();loadFrame();invoke(R,"child","("+FRAME+")"+FRAME,true);mv.visitVarInsn(ASTORE,inner);frame=inner;block(block.body(),true);frame=previous;}
        else if(e instanceof Match match) {match(match);}
        else if(e instanceof TypeTest test) {mv.visitVarInsn(ALOAD,0);expression(test.value());mv.visitLdcInsn(resolved(test.target()));push(test.op().equals("&")?1:0);invoke(R,"satisfies","("+CTX+OBJ+"Ljava/lang/String;Z)Z",true);invoke("java/lang/Boolean","valueOf","(Z)Ljava/lang/Boolean;",true);}
        else if(e instanceof Unary u) {
            Type target=ir.types().get(u);
            if(u.op().startsWith("acknowledge:")){acknowledge(u.value(),u.op().endsWith("true"));mv.visitInsn(ACONST_NULL);}
            else if(u.op().equals("-")&&u.value() instanceof Literal literal&&target!=null&&(literal.value() instanceof Long||literal.value() instanceof java.math.BigInteger)) {
                var value=literal.value() instanceof java.math.BigInteger big?big:java.math.BigInteger.valueOf((Long)literal.value());
                mv.visitLdcInsn(value.negate().toString());mv.visitLdcInsn(target.name());invoke(R,"integerText","(Ljava/lang/String;Ljava/lang/String;)"+OBJ,true);
            } else if(u.op().equals("-")&&u.value() instanceof Literal literal&&literal.value() instanceof Double value&&target!=null) {
                mv.visitLdcInsn(-value);mv.visitLdcInsn(target.name());invoke(R,"floating","(DLjava/lang/String;)"+OBJ,true);
            } else {mv.visitVarInsn(ALOAD,0);mv.visitLdcInsn(u.op());expression(u.value());invoke(R,"unary","("+CTX+"Ljava/lang/String;"+OBJ+")"+OBJ,true);}
        }
        else if(e instanceof Binary b) {
            if(Set.of("and","&&","or","||").contains(b.op())) {
                boolean and=b.op().equals("and")||b.op().equals("&&");var shortCircuit=new Label();var end=new Label();
                expression(b.left());truth();mv.visitJumpInsn(and?IFEQ:IFNE,shortCircuit);expression(b.right());mv.visitJumpInsn(GOTO,end);mv.visitLabel(shortCircuit);bool(!and);mv.visitLabel(end);
            } else if(b.op().equals("?:")) {
                var end=new Label();expression(b.left());mv.visitInsn(DUP);mv.visitJumpInsn(IFNONNULL,end);mv.visitInsn(POP);expression(b.right());mv.visitLabel(end);
            } else {mv.visitVarInsn(ALOAD,0);mv.visitLdcInsn(b.op());expression(b.left());expression(b.right());invoke(R,"binary","("+CTX+"Ljava/lang/String;"+OBJ+OBJ+")"+OBJ,true);}
        } else if(e instanceof ObjectLiteral o) {strings(o.keys());array(o.values());invoke(R,"object","([Ljava/lang/String;"+ARR+")"+OBJ,true);}
        else if(e instanceof ObjectAccess a) {mv.visitVarInsn(ALOAD,0);expression(a.target());expression(a.selector());invoke(R,"get","("+CTX+OBJ+OBJ+")"+OBJ,true);}
        else if(e instanceof VectorAccess a) {call(new Call(a.target(),List.of("nth"),List.of(a.index()),a.span()));}
        else if(e instanceof Get g) {mv.visitVarInsn(ALOAD,0);expression(g.receiver());expression(g.key());invoke(R,"get","("+CTX+OBJ+OBJ+")"+OBJ,true);}
        else if(e instanceof Convert c) {            Type conversion=ir.types().get(c);Type target=conversion!=null&&conversion.name().equals("try")?conversion.args().getFirst():c.target();
            mv.visitVarInsn(ALOAD,0);expression(c.value());mv.visitLdcInsn(resolved(target));invoke(R,c.mode()==ConversionMode.FORCED?"convertForced":"convert","("+CTX+OBJ+"Ljava/lang/String;)"+OBJ,true);
        }
        else if(e instanceof Rescue r) {mv.visitVarInsn(ALOAD,0);expression(r.value());expression(r.handler());invoke(R,"rescue","("+CTX+OBJ+OBJ+")"+OBJ,true);}
        else if(e instanceof Call c)call(c);
        else if(e instanceof Choice c) {var no=new Label();var end=new Label();expression(c.condition());truth();mv.visitJumpInsn(IFEQ,no);acknowledge(c.condition(),true);expression(c.yes());mv.visitJumpInsn(GOTO,end);mv.visitLabel(no);if(!(c.no() instanceof Literal literal&&literal.value()==null))acknowledge(c.condition(),false);expression(c.no());mv.visitLabel(end);}
        else if(e instanceof Range r) {expression(r.start());expression(r.end());expression(r.step());push(r.inclusive()?1:0);invoke(R,"range","("+OBJ+OBJ+OBJ+"Z)"+OBJ,true);}
        else if(e instanceof Lambda l) {mv.visitVarInsn(ALOAD,0);mv.visitLdcInsn(org.objectweb.asm.Type.getObjectType(owner));mv.visitLdcInsn("lambda$"+lambdas.get(l));loadFrame();mv.visitLdcInsn(resolved(ir.types().get(l)));invoke(R,"closure","("+CTX+"Ljava/lang/Class;Ljava/lang/String;"+FRAME+"Ljava/lang/String;)"+OBJ,true);}
        else if(e instanceof Comprehension c)comprehension(c);
        else if(e instanceof Ast.CompactVector v) {mv.visitVarInsn(ALOAD,0);array(v.items());mv.visitLdcInsn(resolved(ir.types().get(v)));invoke(R,"vector","("+CTX+ARR+"Ljava/lang/String;)"+OBJ,true);}
        else if(e instanceof Ast.Vector v) {mv.visitVarInsn(ALOAD,0);array(v.items());mv.visitLdcInsn(resolved(ir.types().get(v)));invoke(R,"vector","("+CTX+ARR+"Ljava/lang/String;)"+OBJ,true);}
        else throw new IllegalStateException("unlowered expression: "+e);
    }
    private void match(Match match) {
        int subject=local();expression(match.value());mv.visitVarInsn(ASTORE,subject);Label done=new Label();int previous=frame;
        for(var arm:match.arms()) {
            Label accepted=new Label(),next=new Label();int inner=local();loadFrame();invoke(R,"child","("+FRAME+")"+FRAME,true);mv.visitVarInsn(ASTORE,inner);frame=inner;
            for(var pattern:arm.patterns()) {
                if(pattern instanceof Name name&&ir.patternBindings().contains(name)) {
                    if(!name.name().equals("_")){loadFrame();mv.visitLdcInsn(name.name());mv.visitVarInsn(ALOAD,subject);invoke(F,"bind","(Ljava/lang/String;"+OBJ+")V",false);}
                    mv.visitJumpInsn(GOTO,accepted);
                } else {mv.visitVarInsn(ALOAD,0);mv.visitVarInsn(ALOAD,subject);expression(pattern);invoke(R,"matchesPattern","("+CTX+OBJ+OBJ+")Z",true);mv.visitJumpInsn(IFNE,accepted);}
            }
            mv.visitJumpInsn(GOTO,next);mv.visitLabel(accepted);
            if(arm.guard()!=null){expression(arm.guard());truth();mv.visitJumpInsn(IFEQ,next);}
            expression(arm.body());mv.visitJumpInsn(GOTO,done);mv.visitLabel(next);frame=previous;
        }
        if(match.otherwise()==null)mv.visitInsn(ACONST_NULL);else expression(match.otherwise());mv.visitLabel(done);frame=previous;
    }
    private void acknowledge(Expr predicate,boolean positive) {
        if(predicate instanceof Unary unary&&unary.op().equals("not")){acknowledge(unary.value(),!positive);return;}
        if(predicate instanceof Call call&&call.receiver()!=null&&call.args().isEmpty()&&((call.selector().equals("fail?")&&positive)||(call.selector().equals("ok?")&&!positive))) {
            mv.visitVarInsn(ALOAD,0);loadFrame();mv.visitLdcInsn(failureSlot(call));invoke(F,"get","(Ljava/lang/String;)"+OBJ,false);invoke(R,"acknowledge","("+CTX+OBJ+")V",true);
        }
    }
    private String failureSlot(Call call){return "&failure@"+call.receiver().span()+":"+call.selector();}
    private void call(Call c) {
        mv.visitVarInsn(ALOAD,0);
        if(c.receiver()==null)mv.visitInsn(ACONST_NULL);
        else {
            Type factory=ir.types().get(c.receiver()),produced=ir.types().get(c);
            while(produced!=null&&Set.of("try","maybe").contains(produced.name()))produced=produced.args().getFirst();
            if(factory!=null&&factory.name().equals("type")&&produced!=null&&(c.selector().equals("new")||c.selector().equals("create")||c.selector().startsWith("new:")||c.selector().startsWith("create:"))&&!produced.args().isEmpty()&&(ir.records().stream().anyMatch(r->r.name().equals(factory.args().getFirst().name()))||mach.semantic.ExtraCollectionContracts.TYPES.contains(factory.args().getFirst().name()))) {
                mv.visitLdcInsn(produced.toString());invoke(R,"type","(Ljava/lang/String;)"+OBJ,true);
            } else expression(c.receiver());
        }
        if(c.receiver()!=null&&c.args().isEmpty()&&Set.of("ok?","fail?").contains(c.selector())) {
            int original=local();mv.visitVarInsn(ASTORE,original);loadFrame();mv.visitLdcInsn(failureSlot(c));mv.visitVarInsn(ALOAD,original);invoke(F,"bind","(Ljava/lang/String;"+OBJ+")V",false);mv.visitVarInsn(ALOAD,original);
        }
        mv.visitLdcInsn(c.selector());array(c.args());
        if(c.block()!=null){expression(c.block());invoke(R,"send","("+CTX+OBJ+"Ljava/lang/String;"+ARR+OBJ+")"+OBJ,true);}
        else invoke(R,"send","("+CTX+OBJ+"Ljava/lang/String;"+ARR+")"+OBJ,true);
    }
}
