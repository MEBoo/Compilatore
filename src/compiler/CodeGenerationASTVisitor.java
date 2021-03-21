package compiler;

import compiler.AST.*;
import compiler.lib.*;
import svm.ExecuteVM;
import compiler.exc.*;
import static compiler.lib.FOOLlib.*;

import java.util.List;
import java.util.ArrayList;

public class CodeGenerationASTVisitor extends BaseASTVisitor<String, VoidException> {

	private static final List<List<String>> dispatchTables = new ArrayList<>();		//MOD (OO) aggiunta elenco dispatch tables
	
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
	public String visitNode(CallNode n) {	//MOD (HO) - la chiamata deve usare come access link il puntatore dell'AR impacchettato nell'ID in chiamata
		if (print) printNode(n,n.id);
		String argCode = null, getAR = null;
		for (int i=n.arglist.size()-1;i>=0;i--) argCode=nlJoin(argCode,visit(n.arglist.get(i)));
		for (int i = 0;i<n.nl-n.entry.nl;i++) getAR=nlJoin(getAR,"lw");
		
		if(n.entry.type instanceof MethodTypeNode) {	// MOD (OO): chiamata di un metodo locale cioè dall'interno di un altro metodo di un oggetto
			return nlJoin(
				"lfp", 			// carico Control Link (puntatore al frame del chiamante)
				argCode, 		// generate code for argument expressions in reversed order
				"lfp", getAR,   // raggiungo l'address del frame contente la dichiarazione di ID, in pratica arrivo all'oggetto sull'heap
	                            // seguendo sempre la catena statica di Access Links (come la normale chiamata di una funzione)
	            "stm", 			// pop dell'Access Link in $tm per duplicarlo
	            "ltm", 			// carico sullo stack l'Access Link (che è l'object pointer)
	            "ltm",        	// duplico il valore precedente, cioè AL / obj pointer
	            "lw",		  	// carica sullo stack l'indirizzo della dispatch table dell'oggetto (perchè l'obj pointer punta direttamente alla cella dove è memorizzato il dispatch pointer)
	            "push "+n.entry.offset, "add", // calcolo indirizzo della cella in dispatch table dove è memorizzato l'indirizzo del metodo
	            "lw", 			// carico sullo stack l'indirizzo del metodo
	            "js" 			// salto
			);
		} else {
			return nlJoin(
				"lfp", 		  // load Control Link (pointer to frame of function "id" caller)
				argCode, 	  // generate code for argument expressions in reversed order
				"lfp", getAR, // retrieve address of frame containing "id" declaration
	                          // by following the static chain (of Access Links)
	            "stm", 		  // set $tm to popped value (lo devo usare 2 volte)
	            "ltm", "push "+n.entry.offset, "add", "lw", 	//carico Access Link - andandolo a prendere nello stack in posizione precedente all'indirizzo della funzione
	            "ltm", "push "+(n.entry.offset-1), "add", "lw", //carico indirizzo funzione
	            "js"  		  // jump to popped address (saving address of subsequent instruction in $ra)
			);
		}
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

	// OBJECT ORIENTED
	
	@Override
	public String visitNode(ClassNode n) {
		if (print) printNode(n);
		
		List<String> dispatchTable;		// ogni classe ha una propria tabella che mappa per ogni metodo: [offset metodo] -> [label metodo]
		if(n.superID != null) 				   											 // se eredito parto con la dispatch table della super-class
			dispatchTable = new ArrayList<>(dispatchTables.get(-n.superEntry.offset-2)); // converto l'offset nell AR globale in indice da 0 a ...
		else
			dispatchTable = new ArrayList<>();

		for(MethodNode m : n.methods) {
			visit(m);
			
			if( m.offset >= dispatchTable.size() || dispatchTable.get(m.offset).isEmpty() )
				dispatchTable.add(m.label); 		 // normalmente si usa l'etichetta del metodo della super-classe 
			else
				dispatchTable.set(m.offset,m.label); // overriding: si usa l'etichetta del metodo della classe che estende
		}
		
		dispatchTables.add(dispatchTable);
		
		String methodCode = null;			// codice per generare sull'heap la dispach table
		for (String m : dispatchTable) {	// per ogni metodo aggiungo la label sull'heap
			methodCode = nlJoin(
				methodCode,
				"push " + m, 	// push della label del metodo
				"lhp", 		 	// push di hp sullo stack, hp contiene l'indirizzo corrente all'heap, su cui devo scrivere la label
				"sw", 		 	// scrivo la label all'indirizzo puntato da hp
				"lhp", "push 1", "add", "shp" // incremento hp per puntare alla cella heap successiva
			); 
		}
		return nlJoin(
			"lhp", 			// metto sullo stack il dispatch pointer che punta alla dispach table della classe
			methodCode  	// poi il codice per generare la dispach table sull'heap 
		);
	}
	
	@Override
	public String visitNode(MethodNode n) {		// come FunNode - però non ritorna l'etichetta
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
		
		n.label = freshFunLabel();
		
		putCode(
			nlJoin(	
				n.label+":",
				"cfp", 		// set $fp to $sp value
				"lra", 		// load $ra value 
				declCode, 	// generate code for local declarations (they use the new $fp!!!)
				visit(n.exp), // generate code for function body expression
				"stm", 		// set $tm to popped value (function result)
				popDecl, 	// remove local declarations from stack
				"sra", 		// set $ra to popped value
				"pop", 		// remove Access Link from stack
				popParl, 	// remove parameters from stack
				"sfp", 		// set $fp to popped value (Control Link)
				"ltm", 		// load $tm value (function result)
				"lra", 		// load $ra value
				"js"  		// jump to to popped address
			)
		);
		
		return null;
	}
	
	@Override
	public String visitNode(EmptyNode n) {
		return nlJoin("push -1"); 	//-1 è un valore diverso da qualsiasi object pointer di qualsiasi oggetto creato
	}
	
	@Override
	public String visitNode(ClassCallNode n) {	// simile a CallNode solo che occorre risalire prima al refID
		if (print) printNode(n,n.refID + "." + n.methodID);
		
		String argCode = null, getAR = null;
		for (int i=n.arglist.size()-1;i>=0;i--) argCode=nlJoin(argCode,visit(n.arglist.get(i)));
		for (int i = 0;i<n.nl-n.entry.nl;i++) getAR=nlJoin(getAR,"lw");
		
		return nlJoin(
			"lfp", 		  // carica control link
			argCode, 	  // genera il codice per gli argomenti
			"lfp", getAR, // risalita catena statica per trovare refID cioè l'object pointer ( refID può essere dichiarato sullo stack o essere un campo sull'heap )
			"push " + n.entry.offset, "add", // calcolo la posizione dell'indirizzo di refID 
			"lw", 		  // carica sullo stack l'indirizzo dell'oggetto che sta sull'heap ( quindi sullo stack ho messo l'Access Link )
			"stm", 		  // pop contenuto stack in tm per duplicare
            "ltm",        // carica Access Link 
            "ltm",        // duplico il valore precedente cioè AL / obj pointer
            "lw",		  // carica sullo stack l'indirizzo della dispatch table dell'oggetto (perchè l'obj pointer punta alla cella dove è memorizzato il dispatch pointer)
            "push "+ n.methodEntry.offset , "add", // calcolo la posizione dell'indirizzo del metodo a cui saltare
			"lw", 		  // carica sullo stack l'indirizzo del metodo
			"js" 		  // e poi salto a quell'indirizzo
		);
	} 
	
	@Override
	public String visitNode(NewNode n) {	// crea l'oggetto sull'heap caricando gli argomenti sull'heap, questi sono i campi dell'oggetto
		if (print) printNode(n);
		String heapCopy = null;
		String argCode = null;

		for (int i = 0; i < n.arglist.size(); i++) {
			argCode = nlJoin(argCode, visit(n.arglist.get(i)));	// codice per caricare gli argomenti sullo stack in ordine
			heapCopy = nlJoin(								    // codice per copiare ogni argomento sull'heap
							heapCopy,
							"lhp", 		// carico sullo stack indirizzo HP (heap pointer)
							"sw", 		// scrittura ad indirizzo di HP dell'argomento / campo 
							"lhp", "push 1", "add", "shp" // incrementa HP di 1
							);
		}
		
		return nlJoin(
				argCode,		// carico gli argomenti tutti sullo stack
				heapCopy,		// sposto gli argomenti nell'heap, sono i campi dell'oggetto 
				"push " + (ExecuteVM.MEMSIZE + n.entry.offset), "lw", // carico sullo stack l'indirizzo della dispatch table (prendendolo dalla dichiarazione della classe sull'AR base nello stack)
				"lhp", "sw",  	// scrivo l'indirizzo della dispatch table all'indirizzo di hp (cioè lo scrivo sull'heap subito dopo/prima dei campi)
				"lhp", 	  	  	// valore lasciato sullo stack al termine di tutto il new(): carico sullo stack il valore di HP che contiene l'object pointer
				"lhp", "push 1", "add", "shp" // incrementa HP di 1
			);
	}
	
}