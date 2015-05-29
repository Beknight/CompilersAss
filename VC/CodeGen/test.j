.class public CodeGen/test
.super java/lang/Object
	
	
	; standard class static initializer 
.method static <clinit>()V
	
	
	; set limits used by this method
.limit locals 0
.limit stack 0
	return
.end method
	
	; standard constructor initializer 
.method public <init>()V
.limit stack 1
.limit locals 1
	aload_0
	invokespecial java/lang/Object/<init>()V
	return
.end method
.method public static main([Ljava/lang/String;)V
L0:
.var 0 is argv [Ljava/lang/String; from L0 to L1
.var 1 is vc$ LCodeGen/test; from L0 to L1
	new CodeGen/test
	dup
	invokenonvirtual CodeGen/test/<init>()V
	astore_1
.var 2 is i F from L0 to L1
	iconst_0
	i2f
	fstore_2
.var 3 is j I from L0 to L1
	iconst_2
	istore_3
.var 4 is x I from L0 to L1
	iload_3
	istore 4
.var 5 is bool Z from L0 to L1
	iconst_0
	istore 5
	fload_2
	iload_3
	i2f
	fadd
	fstore_2
	iload 4
	i2f
	fload_2
	ifgt L2
	iconst_0
	goto L3
L2:
	iconst_1
L3:
	istore 5
	iload 5
	ifeq L4
	iconst_0
	goto L5
L4:
	iconst_1
L5:
	istore 5
	return
L1:
	return
	
	; set limits used by this method
.limit locals 6
.limit stack 5
.end method
