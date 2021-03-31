package compiler;

import compiler.AST.*;
import compiler.lib.*;
import compiler.exc.*;
import static compiler.lib.FOOLlib.*;

public class CodeGenerationASTVisitor extends BaseASTVisitor<String, VoidException> {

  CodeGenerationASTVisitor() {}
  CodeGenerationASTVisitor(boolean debug) {super(false,debug);} //enables print for debugging

	@Override
	public String visitNode(ProgLetInNode n) {
		if (print) printNode(n);
		String declCode = null;
		for (Node dec : n.declist) declCode=nlJoin(declCode,visit(dec));
		return nlJoin(
			"push 0",	
			declCode, // generate code for declarations (allocation)			
			visit(n.exp),
			"halt",
			getCode()
		);
	}

	@Override
	public String visitNode(ProgNode n) {
		if (print) printNode(n);
		return nlJoin(
			visit(n.exp),
			"halt"
		);
	}

	@Override
	public String visitNode(FunNode n) {
		if (print) printNode(n,n.id);
		String declCode = null, popDecl = null, popParl = null;
		
		for (DecNode dec : n.declist) {						//MOD (HO): nel ripulire lo stack ogni dec / par di tipo Arrow occupa 2 posizioni
			declCode = nlJoin(declCode,visit(dec));
			
			if (dec.getType() instanceof ArrowTypeNode)
				popDecl = nlJoin(popDecl,"pop","pop");
			else
				popDecl = nlJoin(popDecl,"pop");
		}
		
		for (int i=0;i<n.parlist.size();i++)
		{
			if (n.parlist.get(i).getType() instanceof ArrowTypeNode)
				popParl = nlJoin(popParl,"pop","pop");
			else
				popParl = nlJoin(popParl,"pop");
		}
		
		String funl = freshFunLabel();
		putCode(
			nlJoin(
				funl+":",
				"cfp", // set $fp to $sp value
				"lra", // load $ra value
				declCode, // generate code for local declarations (they use the new $fp!!!)
				visit(n.exp), // generate code for function body expression
				"stm", // set $tm to popped value (function result)
				popDecl, // remove local declarations from stack
				"sra", // set $ra to popped value
				"pop", // remove Access Link from stack
				popParl, // remove parameters from stack
				"sfp", // set $fp to popped value (Control Link)
				"ltm", // load $tm value (function result)
				"lra", // load $ra value
				"js"  // jump to to popped address
			)
		);
		return nlJoin(			//MOD (HO)
				"lfp",			//carico sullo stack il puntatore all'AR della dichiarazione della funzione - che corrisponde all'attuale Frame Pointer (registro FP)
				"push "+funl
				);		
	}

	@Override
	public String visitNode(VarNode n) {
		if (print) printNode(n,n.id);
		return visit(n.exp);
	}

	@Override
	public String visitNode(PrintNode n) {
		if (print) printNode(n);
		return nlJoin(
			visit(n.exp),
			"print"
		);
	}

	@Override
	public String visitNode(IfNode n) {
		if (print) printNode(n);
	 	String l1 = freshLabel();
	 	String l2 = freshLabel();		
		return nlJoin(
			visit(n.cond),
			"push 1",
			"beq "+l1,
			visit(n.el),
			"b "+l2,
			l1+":",
			visit(n.th),
			l2+":"
		);
	}

	@Override
	public String visitNode(EqualNode n) {
		if (print) printNode(n);
	 	String l1 = freshLabel();
	 	String l2 = freshLabel();
		return nlJoin(
			visit(n.left),
			visit(n.right),
			"beq "+l1,
			"push 0",
			"b "+l2,
			l1+":",
			"push 1",
			l2+":"
		);
	}

	@Override
	public String visitNode(TimesNode n) {
		if (print) printNode(n);
		return nlJoin(
			visit(n.left),
			visit(n.right),
			"mult"
		);	
	}

	@Override
	public String visitNode(PlusNode n) {
		if (print) printNode(n);
		return nlJoin(
			visit(n.left),
			visit(n.right),
			"add"				
		);
	}

	@Override
	public String visitNode(CallNode n) { //MOD (HO) - la chiamata deve usare come access link il puntatore dell'AR impacchettato nell'ID in chiamata
		if (print) printNode(n,n.id);
		String argCode = null, getAR = null;
		for (int i=n.arglist.size()-1;i>=0;i--) argCode=nlJoin(argCode,visit(n.arglist.get(i)));
		for (int i = 0;i<n.nl-n.entry.nl;i++) getAR=nlJoin(getAR,"lw");
		return nlJoin(
			"lfp", // load Control Link (pointer to frame of function "id" caller)
			argCode, // generate code for argument expressions in reversed order
			"lfp", getAR, // retrieve address of frame containing "id" declaration
                          // by following the static chain (of Access Links)
            "stm", // set $tm to popped value (with the aim of duplicating top of stack)
            "ltm", "push "+n.entry.offset, "add", "lw", 	//carico Access Link - andandolo a prendere nello stack in posizione precedente all'indirizzo della funzione
            "ltm", "push "+(n.entry.offset-1), "add", "lw", //carico indirizzo funzione
            "js"  // jump to popped address (saving address of subsequent instruction in $ra)
		);
	}

	@Override
	public String visitNode(IdNode n) {
		if (print) printNode(n,n.id);
		String getAR = null;
		for (int i = 0;i<n.nl-n.entry.nl;i++) getAR=nlJoin(getAR,"lw");
		
		if(n.entry.type instanceof ArrowTypeNode) { 			//MOD (HO) - preparo la closure 
			return nlJoin( 
				"lfp", getAR, // retrieve address of frame containing "id" declaration
				"stm", 		  // set $tm to popped value (salvo l'indirizzo della AR - devo usarlo 2 volte)
		        "ltm", 		  // ricarico sullo stack l'indirizzo dell' AR dove è dichiarato ID
				"push "+n.entry.offset, "add", "lw", //carico l'AR dove è effettivamente dichiarata la funzione (mi servirà come Access Link nella chiamata)
				"ltm", 		  // ricarico sullo stack l'indirizzo dell' AR dove è dichiarato ID
				"push "+(n.entry.offset-1), "add", "lw" // carica l'indirizzo della funzione (la label)
				);
		} else {
			return nlJoin(
				"lfp", getAR, // retrieve address of frame containing "id" declaration
				              // by following the static chain (of Access Links)
				"push "+n.entry.offset, "add", // compute address of "id" declaration
				"lw" 		  // load value of "id" variable
			);
		}
	}

	@Override
	public String visitNode(BoolNode n) {
		if (print) printNode(n,n.val.toString());
		return "push "+(n.val?1:0);
	}

	@Override
	public String visitNode(IntNode n) {
		if (print) printNode(n,n.val.toString());
		return "push "+n.val;
	}
	
	//MOD: NEW GENERATION
	
	//OPERATORS
	
	@Override
	public String visitNode(MinusNode n) { // simile a add
		if (print) printNode(n);
		return nlJoin(
			visit(n.left),
			visit(n.right),
			"sub"				
		);
	}
	
	@Override
	public String visitNode(DivNode n) {  // simile a times
		if (print) printNode(n);
		return nlJoin(
			visit(n.left),
			visit(n.right),
			"div"
		);	
	}
	
	@Override
	public String visitNode(GreaterEqualNode n) { // simile a equal
		if (print) printNode(n);
	 	String l1 = freshLabel();
	 	String l2 = freshLabel();
		return nlJoin(
			visit(n.right),  // inverto gli argomenti rispetto a equal/less equal
			visit(n.left),
			"bleq "+l1,		// uso bleq invece di beq
			"push 0",
			"b "+l2,
			l1+":",
			"push 1",
			l2+":"
		);
	}
	
	@Override
	public String visitNode(LessEqualNode n) { // simile a equal
		if (print) printNode(n);
	 	String l1 = freshLabel();
	 	String l2 = freshLabel();
		return nlJoin(
			visit(n.left), 
			visit(n.right),
			"bleq "+l1,		// uso bleq invece di beq
			"push 0",
			"b "+l2,
			l1+":",
			"push 1",
			l2+":"
		);
	}
	
	@Override
	public String visitNode(NotNode n) { 
		if (print) printNode(n);
		return nlJoin(
			"push 1",		// eseguo l'operazione 1 - n.exp
			visit(n.exp),	// assumo che n.exp sia sicuramente un bool quindi: 0 oppure 1. 
			"sub"			// la soluzione alternativa è usare beq ma richiede più istruzioni	
		);
	}
	
	@Override
	public String visitNode(OrNode n) { // check di ogni argomento a true
		if (print) printNode(n);
	 	String l1 = freshLabel();
	 	String l2 = freshLabel();
		return nlJoin(
			"push 1",		// check primo argomento - se questo è true non valuto il secondo argomento 
			visit(n.left),
			"beq "+l2,		
			"push 1",		// check secondo argomento
			visit(n.right),
			"beq "+l2,		
			"push 0",		// false
			"b " + l1,
			l2+":",			
			"push 1",		// true
			l1+":"
		);
	}
	
	@Override
	public String visitNode(AndNode n) {  // faccio il contrario di OR - check degli argomenti a false 
		if (print) printNode(n);
	 	String l1 = freshLabel();
	 	String l2 = freshLabel();
		return nlJoin(
			"push 0",		// check primo argomento - se questo è false non valuto il secondo argomento
			visit(n.left),
			"beq "+l2,		
			"push 0",		// check secondo argomento
			visit(n.right),
			"beq "+l2,		
			"push 1",		// true
			"b " + l1,
			l2+":",			
			"push 0",		// false
			l1+":"
		);
	}

	// NOTA su AND & OR : entrambe le procedure si potrebbero implementare più brevi seguendo il metodo usato per NOT (con sub / add)
	// Tuttavia così facendo si valuterebbero sempre entrambi gli argomenti, cosa che gli altri linguaggi in genere non fanno.
}