package compiler;

import java.util.*;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import compiler.AST.*;
import compiler.FOOLParser.*;
import compiler.lib.*;
import static compiler.lib.FOOLlib.*;

public class ASTGenerationSTVisitor extends FOOLBaseVisitor<Node> {

	String indent;
    public boolean print;
	
    ASTGenerationSTVisitor() {}    
    ASTGenerationSTVisitor(boolean debug) { print=debug; }
        
    private void printVarAndProdName(ParserRuleContext ctx) {
        String prefix="";        
    	Class<?> ctxClass=ctx.getClass(), parentClass=ctxClass.getSuperclass();
        if (!parentClass.equals(ParserRuleContext.class)) // parentClass is the var context (and not ctxClass itself)
        	prefix=lowerizeFirstChar(extractCtxName(parentClass.getName()))+": production #";
    	System.out.println(indent+prefix+lowerizeFirstChar(extractCtxName(ctxClass.getName())));                               	
    }
        
    @Override
	public Node visit(ParseTree t) {
    	if (t==null) return null;
        String temp=indent;
        indent=(indent==null)?"":indent+"  ";
        Node result = super.visit(t);
        indent=temp;
        return result; 
	}

	@Override
	public Node visitProg(ProgContext c) {
		if (print) printVarAndProdName(c);
		return visit(c.progbody());
	}

	@Override
	public Node visitLetInProg(LetInProgContext c) {
		if (print) printVarAndProdName(c);
		List<DecNode> declist = new ArrayList<>();
		for (CldecContext cla : c.cldec()) declist.add((DecNode)visit(cla));	//MOD (OO): se ci sono classi vengono dichiarate prima delle altre dec
		for (DecContext dec : c.dec()) declist.add((DecNode) visit(dec));
		return new ProgLetInNode(declist, visit(c.exp()));
	}

	@Override
	public Node visitNoDecProg(NoDecProgContext c) {
		if (print) printVarAndProdName(c);
		return new ProgNode(visit(c.exp()));
	}

	@Override
	public Node visitTimesDiv(TimesDivContext c) {		//MOD: aggiunto supporto a Div
		if (print) printVarAndProdName(c);
		
		Node n;
		
		if (c.TIMES() != null) {
			n = new TimesNode(visit(c.exp(0)), visit(c.exp(1)));
			n.setLine(c.TIMES().getSymbol().getLine());
		} else {
			n = new DivNode(visit(c.exp(0)), visit(c.exp(1)));
			n.setLine(c.DIV().getSymbol().getLine());
		}
		
        return n;		
	}

	@Override
	public Node visitPlusMinus(PlusMinusContext c) {	//MOD: aggiunto supporto a Minus
		if (print) printVarAndProdName(c);
		
		Node n;
		
		if (c.PLUS()!=null) {
			n = new PlusNode(visit(c.exp(0)), visit(c.exp(1)));
			n.setLine(c.PLUS().getSymbol().getLine());	
		} else {
			n = new MinusNode(visit(c.exp(0)), visit(c.exp(1)));
			n.setLine(c.MINUS().getSymbol().getLine());
		}
		
        return n;		
	}

	@Override
	public Node visitComp(CompContext c) {		//MOD: aggiunto supporto a GreaterEqual + LessEqual
		if (print) printVarAndProdName(c);
		
		Node n;
		
		if(c.EQ() != null) {
			n = new EqualNode(visit(c.exp(0)), visit(c.exp(1)));
			n.setLine(c.EQ().getSymbol().getLine());
		} else if (c.GE() != null) {
			n = new GreaterEqualNode(visit(c.exp(0)), visit(c.exp(1)));
			n.setLine(c.GE().getSymbol().getLine());
		} else {
			n = new LessEqualNode(visit(c.exp(0)), visit(c.exp(1)));
			n.setLine(c.LE().getSymbol().getLine());
		}
		
        return n;		
	}

	@Override
	public Node visitVardec(VardecContext c) {
		if (print) printVarAndProdName(c);
		Node n = null;
		if (c.ID()!=null) { //non-incomplete ST
			n = new VarNode(c.ID().getText(), (TypeNode) visit(c.hotype()), visit(c.exp())); //MOD: hotype
			n.setLine(c.VAR().getSymbol().getLine());
		}
        return n;
	}

	@Override
	public Node visitFundec(FundecContext c) {
		if (print) printVarAndProdName(c);
		List<ParNode> parList = new ArrayList<>();
		
		for (int i = 1; i < c.ID().size(); i++) { 
			ParNode p = new ParNode(c.ID(i).getText(),(TypeNode) visit(c.hotype(i-1))); //MOD: hotype
			p.setLine(c.ID(i).getSymbol().getLine());
			parList.add(p);
		}
		
		List<DecNode> decList = new ArrayList<>();
		for (DecContext dec : c.dec()) decList.add((DecNode) visit(dec));
		Node n = null;										
		if (c.ID().size()>0) { //non-incomplete ST
			n = new FunNode(c.ID(0).getText(),(TypeNode)visit(c.type()),parList,decList,visit(c.exp()));
			n.setLine(c.FUN().getSymbol().getLine());
		}
        return n;
	}

	@Override
	public Node visitIntType(IntTypeContext c) {
		if (print) printVarAndProdName(c);
		return new IntTypeNode();
	}

	@Override
	public Node visitBoolType(BoolTypeContext c) {
		if (print) printVarAndProdName(c);
		return new BoolTypeNode();
	}

	@Override
	public Node visitInteger(IntegerContext c) {
		if (print) printVarAndProdName(c);
		int v = Integer.parseInt(c.NUM().getText());
		return new IntNode(c.MINUS()==null?v:-v);
	}

	@Override
	public Node visitTrue(TrueContext c) {
		if (print) printVarAndProdName(c);
		return new BoolNode(true);
	}

	@Override
	public Node visitFalse(FalseContext c) {
		if (print) printVarAndProdName(c);
		return new BoolNode(false);
	}

	@Override
	public Node visitIf(IfContext c) {
		if (print) printVarAndProdName(c);
		Node ifNode = visit(c.exp(0));
		Node thenNode = visit(c.exp(1));
		Node elseNode = visit(c.exp(2));
		Node n = new IfNode(ifNode, thenNode, elseNode);
		n.setLine(c.IF().getSymbol().getLine());			
        return n;		
	}

	@Override
	public Node visitPrint(PrintContext c) {
		if (print) printVarAndProdName(c);
		return new PrintNode(visit(c.exp()));
	}

	@Override
	public Node visitPars(ParsContext c) {
		if (print) printVarAndProdName(c);
		return visit(c.exp());
	}

	@Override
	public Node visitId(IdContext c) {
		if (print) printVarAndProdName(c);
		Node n = new IdNode(c.ID().getText());
		n.setLine(c.ID().getSymbol().getLine());
		return n;
	}

	@Override
	public Node visitCall(CallContext c) {
		if (print) printVarAndProdName(c);		
		List<Node> arglist = new ArrayList<>();
		for (ExpContext arg : c.exp()) arglist.add(visit(arg));
		Node n = new CallNode(c.ID().getText(), arglist);
		n.setLine(c.ID().getSymbol().getLine());
		return n;
	}
	
	//MOD: NEW VISITS
	
	@Override
	public Node visitNot(NotContext c) {
		if (print) printVarAndProdName(c);
		
		Node n = new NotNode(visit(c.exp()));
		n.setLine(c.NOT().getSymbol().getLine());	
        return n;		
	}
	
	@Override
	public Node visitAndOr(AndOrContext c) {
		if (print) printVarAndProdName(c);
		
		Node n = null;
		
		if(c.AND() != null) {
			n = new AndNode(visit(c.exp(0)), visit(c.exp(1)));
			n.setLine(c.AND().getSymbol().getLine());	
		} else {
			n = new OrNode(visit(c.exp(0)), visit(c.exp(1)));
			n.setLine(c.OR().getSymbol().getLine());
		}
		
		return n;
	}
	
	// HIGHER ORDER
	
	@Override
	public Node visitArrow(ArrowContext c) { // hotype di tipo arrow esempio: (hotype,hotype)=>type 
		if (print) printVarAndProdName(c);
		
		List<TypeNode> parList = new ArrayList<>();	 // carico i parametri che sono hotype (quindi ricorsivamente potrebbero anche essere di tipo arrow)
		for (int i = 0; i < c.hotype().size(); i++)
			parList.add((TypeNode) visit(c.hotype(i)));
		
		Node n = new ArrowTypeNode(parList, (TypeNode)visit(c.type()));  // il ritorno è sempre type
		n.setLine(c.ARROW().getSymbol().getLine());
		return n;
	}
	
	// OBJECT ORIENTED
	
	@Override
	public Node visitCldec(CldecContext c) { // nelle classi di FOOL i fields sono tutti dichiarati similmente ai pars delle funzioni: ClasseA (field1:int, field2:bool)
		if (print) printVarAndProdName(c);
		
		String superID = null;
		int startIndex = 1;
		
		if(c.EXTENDS() != null) {
			superID = c.ID(1).getSymbol().getText();
			startIndex = 2;		// se la classe estende, gli ID dei fields partono dall'indice 2 anzichè dall'indice 1
		}

		List<FieldNode> fields = new ArrayList<>();
		for (int i = startIndex; i < c.ID().size(); i++) {
			FieldNode f = new FieldNode(c.ID(i).getText(), (TypeNode) visit(c.type(i-startIndex))); // i type dei fields partono sempre dall'indice 0
			f.setLine(c.ID(i).getSymbol().getLine());
			fields.add(f);
		}
		
		List<MethodNode> methods = new ArrayList<>();
		for (MethdecContext dec : c.methdec()) methods.add((MethodNode)visit(dec));
		
		Node n = null;
		if (c.ID().size()>0) { //non-incomplete ST
			n = new ClassNode(c.ID(0).getText(), fields, methods, superID);
			n.setLine(c.ID(0).getSymbol().getLine());
		}
        return n;
	}
	
	@Override
	public Node visitMethdec(MethdecContext c) {	// esattamente come FunDec
		if (print) printVarAndProdName(c);
		
		List<ParNode> parList = new ArrayList<>();
		for (int i = 1; i < c.ID().size(); i++) {
			ParNode p = new ParNode(c.ID(i).getText(), (TypeNode) visit(c.hotype(i-1)));
			p.setLine(c.ID(i).getSymbol().getLine());
			parList.add(p);
		}
		
		List<DecNode> decList = new ArrayList<>();
		for (DecContext dec : c.dec()) decList.add((DecNode) visit(dec));
		
		Node n = null;
		if (c.ID().size()>0) { //non-incomplete ST
			n = new MethodNode(c.ID(0).getText(),(TypeNode) visit(c.type()),parList,decList,visit(c.exp()));
			n.setLine(c.FUN().getSymbol().getLine());
		}
        return n;
	}
	
	@Override
	public Node visitDotCall(DotCallContext c) { // chiamata di un metodo: ID1.ID2(par1,pars2) - Simile a call
		if (print) printVarAndProdName(c);
		
		List<Node> arglist = new ArrayList<>(); // lista argomenti da passare alla chiamata del metodo
		for (ExpContext arg : c.exp()) arglist.add(visit(arg));
		
		Node n = new ClassCallNode(c.ID(0).getText(), c.ID(1).getText(), arglist); // il primo ID è un refTypeNode (che all'interno contiene l'ID della classe a cui si riferisce)
		n.setLine(c.ID(0).getSymbol().getLine());
		return n;
	}
	
	@Override
	public Node visitNew(NewContext c) {	// simile a call (le classi di Fool non hanno costruttori, ogni field è passato come argomento con il new)
		if (print) printVarAndProdName(c);
		
		List<Node> arglist = new ArrayList<>();
		for (ExpContext arg : c.exp()) arglist.add(visit(arg));
		
		Node n = new NewNode(c.ID().getText(), arglist);	// ID = ID della classe
		n.setLine(c.ID().getSymbol().getLine());
		return n;
	}
	
	@Override
	public Node visitIdType(IdTypeContext c) {
		if (print) printVarAndProdName(c);
		
		RefTypeNode n = new RefTypeNode(c.ID().getText());
		n.setLine(c.ID().getSymbol().getLine());
		return n;
	}
	
	@Override
	public Node visitNull(NullContext c) {
		if (print) printVarAndProdName(c);
		
		Node n = new EmptyNode();
		n.setLine(c.NULL().getSymbol().getLine());
		return n;
	}
	
	
}
