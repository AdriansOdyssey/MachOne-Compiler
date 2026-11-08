grammar MachOne;
@header {package mach.frontend.generated;}
@parser::members {
    private mach.frontend.MessageSignatures signatures = new mach.frontend.MessageSignatures();
    public void setMessageSignatures(mach.frontend.MessageSignatures value) { signatures = value; }
    private boolean implicitStart() { return signatures.implicitStart(_input); }
    private boolean knownImplicitStart() { return signatures.knownImplicitStart(_input); }
    private boolean takesArguments(String selector) { return signatures.takesArguments(selector, _input); }
    private boolean positionalContinuation(String selector) { return signatures.positionalContinuation(selector, _input); }
    private boolean commaContinuation(java.util.List<String> parts) { return signatures.commaContinuation(parts, _input); }
}
program: body EOF;
body: SEP* (statement (SEP+ statement)* SEP*)?;
statement
 : '@for' ID 'in' expr SEP body ('@end'|'end') #compileFor
 | '@if' expr SEP body ('@elsif' expr SEP body)* ('@else' SEP body)? '@end' #compileIf
 | 'module' modulePath #moduleDecl
 | 'require' requireTarget #requireDecl
 | ('let'|'final'|'var') name typeRef? '=' expr #binding
 | name ('='|'+='|'-=') expr #assignment
 | 'return' expr? #ret
 | 'on' methodHead ('->' typeRef)? (',' 'if' expr)? ('=' expr | SEP body 'end') #method
 | 'record' ID ('[' ID (',' ID)* ']')? ('&' typeRef)? ('(' ID (',' ID)* ')')? SEP* (recordMember SEP+)* 'end' #recordDecl
 | 'impl' ID '&' typeRef SEP body 'end' #boundedImplDecl
 | 'impl' typeRef (SEP body 'end' | statement) #implDecl
 | 'abstract' 'data' ID SEP* body 'end' #abstractData
 | 'abstract' ID SEP body 'end' #abstractDispatch
 | 'data' ID '=' typeRef #alias
 | 'sign' '{' signItem (',' signItem)* ('->' typeRef)? '}' #signature
 | 'if' expr 'do'? SEP body ('elsif' expr 'do'? SEP body)* ('else' 'do'? SEP body)? 'end' #conditional
 | 'unless' expr SEP body 'end' #unless
 | 'being' ID '=' expr ('while'|'until') expr SEP body 'end' #beingLoop
 | ('while'|'until') expr SEP body 'end' #whileLoop
 | 'loop' ('do' SEP*)? body 'end' #loopStmt
 | 'for' ID (',' ID)? 'in' expr SEP body 'end' #forLoop
 | 'break' #breakStmt
 | 'next' #nextStmt
 | expr #expressionStmt
 ;
field: ID+ typeRef ('{' ID (',' ID)* '}')?;
recordMember: field | 'use' typeRef 'as' ID;
modulePath: ID ('/' ID)*;
requireTarget: modulePath ('/' '*')? ('/'? requireSelection)? ('as' ID)?;
requireSelection: '{' requireEntry (',' requireEntry)* '}';
requireEntry: ID ('as' ID)?;
selector: ID | OF | NTH | templateName | 'then' | 'otherwise' | 'sign';
messageSelector: selector | 'to' | 'by' | 'default';
templateName: ID splice (ID? splice)* ID? | splice (ID? splice)* ID?;
OF: 'of';
NTH: 'nth';
splice: '\'(' expr ')';
methodHead: selector (':' parameterGroup (','? selector ':' parameterGroup)*)?
 | 'op' operator ':'? parameterGroup?;
operator: '+'|'-'|'*'|'/'|'//'|'%'|'**'|'=='|'!='|'<'|'>'|'<='|'>='|'like'|'~'|'and'|'or'|'not'|'&&'|'||'|'&&&'|'|||'|'^^^'|'~~~'|'<<<'|'>>>'|'<<'|'>>'|'|'|'&'|'?:'|'\'s'|'\\\\';
parameterGroup: parameter | '[' parameter (',' parameter)* ']' | '...' ID typeRef?;
parameter: ID typeRef?;
name: ID | MUT | 'self' | '&attrs' | '&block' | '&it';
typeRef: typeCore ('&' typeCore)?;
typeCore: '&'? typeAtom;
typeAtom: (ID | 'nil') ('[' typeArgument (','? typeArgument)* ']')?;
typeArgument: typeRef | '[' typeArgument (','? typeArgument)* ']';
signItem: typeRef | '[' typeRef (',' typeRef)* ']';
expr: implicitSend | opExpr;
implicitSend: {implicitStart()}? message;
message locals [java.util.List<String> parsedParts = new java.util.ArrayList<>()]
 : first=messagePart {$parsedParts.add($first.partName);}
   ( {commaContinuation($parsedParts)}? ',' SEP* next=messagePart {$parsedParts.add($next.partName);}
   | next=messagePart {$parsedParts.add($next.partName);}
   )* anonBlock?
 ;
anonBlock: '=>' opExpr;
messagePart returns [String partName]
 : s=messageSelector {$partName=$s.text;} (':' SEP*)? ({takesArguments($s.text)}? arguments[$s.text])?
 | o=operator ':' {$partName=$o.text;} SEP* ({takesArguments($o.text)}? arguments[$o.text])?
 ;
arguments[String partSelector]: messageArgument ({positionalContinuation($partSelector)}? ',' SEP* messageArgument)*;
messageArgument: {knownImplicitStart()}? implicitSend | opExpr;
opExpr: 'for' ID (',' ID)? 'in' expr 'yield' comprehensionValue ('if' expr)? #comprehensionExpr
 | 'if' expr 'do' SEP body ('elsif' expr 'do' SEP body)* ('else' 'do' SEP body)? 'end' #ifValueExpr
 | 'when' expr SEP+ matchArm+ ('default' '=>' armValue SEP*)? 'end' #matchExpr
 | 'if' expr 'yield' expr 'else' expr #ternaryExpr
 | '(' SEP* expr SEP* ')' message #sendExpr
 | indexExpr NTH opExpr #vectorAccessExpr
 | opExpr OF opExpr #objectAccessExpr
 | opExpr '\'s' fieldKey #fieldAccessExpr
 | opExpr '::' typeRef #conversionExpr
 | opExpr 'rescue' opExpr #rescueExpr
 | <assoc=right> opExpr '**' opExpr #powerExpr
 | '-' opExpr #negExpr
 | '~~~' opExpr #bitNotExpr
 | opExpr ('*'|'/'|'//'|'%') opExpr #mulExpr
 | opExpr ('+'|'-') opExpr #addExpr
 | opExpr ('<<<'|'>>>') opExpr #shiftExpr
 | opExpr '&&&' opExpr #bitAndExpr
 | opExpr '^^^' opExpr #bitXorExpr
 | opExpr '|||' opExpr #bitOrExpr
 | opExpr ('<<'|'>>'|'|') opExpr #collectionExpr
 | opExpr '\\\\' deletionArgs #deletionExpr
 | opExpr ('..'|'to') opExpr ('by' opExpr)? #rangeExpr
 | opExpr ('<'|'>'|'<='|'>=') opExpr #compareExpr
 | opExpr ('=='|'!='|'like'|'~') opExpr #equalExpr
 | opExpr ('isa'|'&') typeRef #typeTestExpr
 | 'not' opExpr #notExpr
 | opExpr ('and'|'&&') opExpr #andExpr
 | opExpr ('or'|'||') opExpr #orExpr
 | opExpr '?:' opExpr #elvisExpr
 | '(' SEP* expr SEP* ')' #parenExpr
 | ('|' (parameter (',' parameter)*)? '|' | '||') ('->' typeRef)? ('{' body '}' | 'do' body 'end' | SEP body 'end') #lambdaExpr
 | 'do' body ('otherwise' 'do' body)? 'end' #doExpr
 | '{' body '}' #blockExpr
 | ('#{' | '{') SEP* (objectKey ':' expr (',' SEP* objectKey ':' expr)*)? SEP* '}' #objectExpr
 | '[' SEP* (expr (',' SEP* expr)*)? SEP* ']' #vectorExpr
 | '%[' SEP* (opExpr (SEP* opExpr)*)? SEP* ']' #compactVectorExpr
 | '%{' SEP* (objectKey ':' opExpr (SEP* objectKey ':' opExpr)*)? SEP* '}' #compactObjectExpr
 | RAW_STRING #rawStringExpr
 | REGEX #regexExpr
 | CHAR #charExpr
 | HEREDOC #heredocExpr
 | INT #integerExpr
 | FLOAT #floatExpr
 | STRING #stringExpr
 | ('true'|'false') #boolExpr
 | 'nil' #nilExpr
 | ID { _input.LT(-1).getStopIndex()+1 == _input.LT(1).getStartIndex() }? '[' typeArgument (','? typeArgument)* ']' #typeAppExpr
 | '#' (ID | splice | operator) #tagExpr
 | templateName #templateExpr
 | splice #spliceExpr
 | name #nameExpr
 ;
fieldKey: '#' (ID | splice) | name | '(' expr ')' | '[' (expr (',' expr)*)? ']';
indexExpr
 : indexAtom (('..'|'to') indexAtom ('by' indexAtom)?)?
 | '[' SEP* (opExpr (',' SEP* opExpr)*)? SEP* ']'
 ;
indexAtom: '-'? (INT | name) | '(' expr ')';
deletionArgs: opExpr (',' opExpr)*;
comprehensionValue: '(' opExpr ':' expr ')' | expr;
matchArm: 'is' opExpr (',' opExpr)* (',' 'if' expr)? '=>' armValue SEP*;
armValue: SEP body | expr;
objectKey: '#' (ID | splice) | ID | templateName;
DOC: '@doc' .*? '@end' -> skip;
COMMENT: ';' ~[\r\n]* -> skip;
MUT: '@' [a-zA-Z_] [a-zA-Z_0-9]*;
ID: [a-zA-Z_] [a-zA-Z_0-9]* '?'?;
FLOAT: [0-9]+ ( '_' [0-9]+ )* '.' [0-9]+;
INT: [0-9]+ ('_' [0-9]+)*;
REGEX: '~"' ~["]* '"';
RAW_STRING: '\\' '"' ~["]* '"';
CHAR: '`' ('\\' . | ~[`\\])* '`';
HEREDOC: 'begin{' [A-Za-z_0-9]+ '}' '\r'? '\n' (.*? '\r'? '\n')? [ \t]* 'end{' [A-Za-z_0-9]+ '}';
STRING: '"' ('\\' . | ~["\\])* '"';
SEP: '\r'? '\n' | '.';
WS: [ \t\r]+ -> skip;
