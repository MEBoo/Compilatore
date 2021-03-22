package compiler;

import compiler.AST.*;
import compiler.exc.*;
import compiler.lib.*;

import java.util.*;

public class SymbolTableASTVisitor extends BaseASTVisitor<Void,VoidException> {
	
	private List<Map<String, STentry>> symTable = new ArrayList<>();
	private Map<String, Map<String,STentry> > classTable = new HashMap<>(); //MOD (OO): per ogni classe dichiarata vado a memorizzare campi e metodi
																			//          serve per poter effettuare l'estensione di una classe
	private int nestingLevel=0; // current nesting level
	private int decOffset=-2; // counter for offset of local declarations at current nesting level 
	int stErrors=0;

	SymbolTableASTVisitor() {}
	SymbolTableASTVisitor(boolean debug) {super(debug);} // enables print for debugging

	private STentry stLookup(String id) {
		int j = nestingLevel;
		STentry entry = null;
		while (j >= 0 && entry == null) 
			entry = symTable.get(j--).get(id);	
		return entry;
	}

	@Override
	public Void visitNode(ProgLetInNode n) {
		if (print) printNode(n);
		Map<String, STentry> hm = new HashMap<>();
		symTable.add(hm);
	    for (Node dec : n.declist) 
	    	visit(dec);
		visit(n.exp);
		symTable.remove(0);
		return null;
	}

	@Override
	public Void visitNode(ProgNode n) {
		if (print) printNode(n);
		visit(n.exp);
		return null;
	}
	
	@Override
	public Void visitNode(FunNode n) {
		if (print) printNode(n);
		
		Map<String, STentry> hm = symTable.get(nestingLevel);
		List<TypeNode> parTypes = new ArrayList<>();  
		
		for (ParNode par : n.parlist) parTypes.add(par.getType()); 
		
		n.setType(new ArrowTypeNode(parTypes,n.retType));   	//MOD: setto il tipo che non è settato come per gli altri DecNode nella fase ASTGeneration 
																//     ha senso non settarlo durante l'ASTgeneration? Forse si perchè in quella fase vengono settati i tipi parsati
																//	   questo invece è un tipo derivato dall'analisi del nodo, stessa cosa vale per il tipo di una "classe"

		visit(n.getType()); 									//MOD: verifico se è coinvolto un tipo RefType e in tal caso verifico la dichiarazione della classe a cui si riferisce
		
		STentry entry = new STentry(nestingLevel,n.getType() ,decOffset);	//MOD (HO): l'offset va decrementato di 2 anziché di 1
		decOffset-=2;
		
		//inserimento di ID nella symtable
		if (hm.put(n.id, entry) != null) {
			System.out.println("Fun id " + n.id + " at line "+ n.getLine() +" already declared");
			stErrors++;
		} 
		
		//creare una nuova hashmap per la symTable
		nestingLevel++;
		Map<String, STentry> hmn = new HashMap<>();
		symTable.add(hmn);
		
		int parOffset=1;
		for (ParNode par : n.parlist) {
			
			if(par.getType() instanceof ArrowTypeNode) //MOD (HO): l'offset va incrementato di 2 anzichè 1 - lo pre-incremento per usare lo slot aggiuntivo necessario
				parOffset++;
			
			if (hmn.put(par.id, new STentry(nestingLevel,par.getType(),parOffset++)) != null) {
				System.out.println("Par id " + par.id + " at line "+ n.getLine() +" already declared");
				stErrors++;
			}
		}
		
		int prevNLDecOffset=decOffset; // stores counter for offset of declarations at previous nesting level 
		decOffset=-2;
		for (Node dec : n.declist) visit(dec);
		visit(n.exp);
		//rimuovere la hashmap corrente poiche' esco dallo scope               
		symTable.remove(nestingLevel--);
		decOffset=prevNLDecOffset; // restores counter for offset of declarations at previous nesting level 
		return null;
	}
	
	@Override
	public Void visitNode(VarNode n) {
		if (print) printNode(n);
		visit(n.exp);
		Map<String, STentry> hm = symTable.get(nestingLevel);
		
		visit(n.getType()); 						//MOD: verifico se è coinvolto un tipo RefType e in tal caso verifico la dichiarazione della classe a cui si riferisce
		
		STentry entry = new STentry(nestingLevel,n.getType(),decOffset--);
		if(n.getType() instanceof ArrowTypeNode) 	//MOD: (HO) l'offset va decrementato di 2
			decOffset--;
		
		//inserimento di ID nella symtable
		if (hm.put(n.id, entry) != null) {
			System.out.println("Var id " + n.id + " at line "+ n.getLine() +" already declared");
			stErrors++;
		}
		return null;
	}

	@Override
	public Void visitNode(PrintNode n) {
		if (print) printNode(n);
		visit(n.exp);
		return null;
	}

	@Override
	public Void visitNode(IfNode n) {
		if (print) printNode(n);
		visit(n.cond);
		visit(n.th);
		visit(n.el);
		return null;
	}
	
	@Override
	public Void visitNode(EqualNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}
	
	@Override
	public Void visitNode(TimesNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}
	
	@Override
	public Void visitNode(PlusNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}

	@Override
	public Void visitNode(CallNode n) {
		if (print) printNode(n);
		STentry entry = stLookup(n.id);
		if (entry == null) {
			System.out.println("Fun id " + n.id + " at line "+ n.getLine() + " not declared");
			stErrors++;
		} else {
			n.entry = entry;
			n.nl = nestingLevel;
		}
		for (Node arg : n.arglist) visit(arg);
		return null;
	}

	@Override
	public Void visitNode(IdNode n) {
		if (print) printNode(n);
		STentry entry = stLookup(n.id);
		if (entry == null) {
			System.out.println("Var or Par id " + n.id + " at line "+ n.getLine() + " not declared");
			stErrors++;
		} else {
			n.entry = entry;
			n.nl = nestingLevel;
		}
		return null;
	}

	@Override
	public Void visitNode(BoolNode n) {
		if (print) printNode(n, n.val.toString());
		return null;
	}

	@Override
	public Void visitNode(IntNode n) {
		if (print) printNode(n, n.val.toString());
		return null;
	}
	
	//MOD: NEW VISITS
	
	// OPERATORS
	
	@Override
	public Void visitNode(MinusNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}
	
	@Override
	public Void visitNode(DivNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}
	
	@Override
	public Void visitNode(GreaterEqualNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}
	
	@Override
	public Void visitNode(LessEqualNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}
	
	@Override
	public Void visitNode(NotNode n) {
		if (print) printNode(n);
		visit(n.exp);
		return null;
	}
	
	@Override
	public Void visitNode(OrNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}
	
	@Override
	public Void visitNode(AndNode n) {
		if (print) printNode(n);
		visit(n.left);
		visit(n.right);
		return null;
	}
	
	// OBJECT ORIENTED
	
	@Override
	public Void visitNode(ClassNode n) {
		if (print) printNode(n, n.id);
		Map<String, STentry> hm = symTable.get(nestingLevel);	// nestingLevel è sempre 0 per le dichiarazioni delle classi

		ClassTypeNode type = null;
		if (n.superID != null) { 								// se la classe eredita, devo partire dalla STentry della super-classe
			if (hm.containsKey(n.superID)) {
				n.superEntry = hm.get(n.superID);									// collego la super-entry
				
				ClassTypeNode superType = ((ClassTypeNode)n.superEntry.type);		// in particolare il tipo della classe usa come base il tipo della super-classe
				type=new ClassTypeNode(new ArrayList<>(superType.allFields),new ArrayList<>(superType.allMethods));
			} else {
				System.out.println("Class id " + n.superID + " at line "+ n.getLine() +" not declared");
				stErrors++;
			}
		} 

		if (type==null)
			type=new ClassTypeNode(new ArrayList<>(), new ArrayList<>());
		
		n.setType(type);												// instanto setto il type, verrà aggiornato qui sotto con campi e metodi
		
		STentry entry = new STentry(nestingLevel,type , decOffset--);	// la dichiarazione della classe sfrutta 1 elemento dello stack
																		// infatti l'AR dove è dichiarata la classe è sempre nel nesting-level 0 (ProgLetIn) 
																		// quindi quando si fa ClassCall si usa l'AR globale per settare l'access-link.
		
		Map<String, STentry> vt = null; 					 		 	// virtual-table - elemento da inserire in class-table (serve per permettere l'ereditarietà)
		if (n.superID != null && classTable.containsKey(n.superID))   
			vt = new HashMap<>(classTable.get(n.superID)); 			 	// se la classe eredita, allora copio il suo vt dalla class-table
		else
			vt = new HashMap<>();
		
		//inserimento di ID nella symTable
		if (hm.put(n.id, entry) != null) {
			System.out.println("Class id " + n.id + " at line "+ n.getLine() +" already declared");
			stErrors++;
		} 
		
		//inserimento di virtual-table in class-table
		if (classTable.put(n.id, vt) != null) {
			System.out.println("Class id " + n.id + " at line "+ n.getLine() +" already declared"); // se passa il controllo sopra questo non dovrebbe capitare - ma non si sa mai
			stErrors++;
		} 
		
		//creare una nuova hashmap per la symTable - vado ad usare la vt appena creata (referenziandola - senza copiarla)
		nestingLevel++;
		Map<String, STentry> hmn = vt; 
		symTable.add(hmn);
		
		int fieldOffset = -1;
		if (n.superID != null) { 						// se si sta ereditando allora il fieldOffset deve considerare i campi della super-classe
			fieldOffset -= type.allFields.size();
		}
		
		HashSet<String> locals = new HashSet<>();		// OTTIMIZZAZIONE: mi serve a verificare se localmente è già definito un campo/metodo 
														// non posso infatti verificare la hmn dato che questa contiene la vt che potrebbe contenere la Dec della super-classe
		
		// i fields vengono gestiti similmente ai parametri di una funzione - non viene eseguita la visita (non ci sono espressioni da valutare)
		for (FieldNode f : n.fields) {
			
			if (locals.contains(f.id)) {				// OTTIMIZZAZIONE: nomi di campi o metodi non possono essere dichiarati 2 volte in una stessa classe
				System.out.println("Field id " + f.id + " at line "+ f.getLine() +" already declared");
				stErrors++;
				continue;
			} 
			
			visit(f.getType()); 						//MOD: verifico se è coinvolto un tipo RefType e in tal caso verifico la dichiarazione della classe a cui si riferisce
			
			locals.add(f.id);
		
			STentry fieldEntry = null;
			
			if(vt.containsKey(f.id)) {		// se il campo è già in vt allora faccio override
				
				STentry superFieldEntry = vt.get(f.id);
				
				if (superFieldEntry.type instanceof MethodTypeNode)	// non consento overriding di un super-metodo con un campo
				{
					System.out.println("Overriding with field " + f.id + " at line "+ f.getLine() +" not allowed");
					stErrors++;
					locals.remove(f.id);	// lo tolgo per permettere la successiva ri-definizione corretta dato che al momento è stato skippato
					continue;
				}
				
				f.offset=superFieldEntry.offset;								// memorizzo l'offset - utile per fase di type-checking
					
				fieldEntry = new STentry(nestingLevel,f.getType(),f.offset); 	// overriding del campo: mantengo l'offset e cambio il tipo
				type.allFields.set(-f.offset-1, fieldEntry.type);				// aggiorno il tipo della classe modificando il tipo del campo 
			} else {
				
				f.offset=fieldOffset--;											// memorizzo l'offset - utile per fase di type-checking
				
				fieldEntry = new STentry(nestingLevel,f.getType(),f.offset);
				type.allFields.add(fieldEntry.type);							// aggiorno il tipo della classe aggiungendo il tipo del campo
			}
			
			vt.put(f.id, fieldEntry);
		}
		
		int prevNLDecOffset=decOffset; 
		decOffset=0; 				   		// offset dei metodi parte da 0
		if (n.superID != null)		   		// se si sta ereditando allora il decOffset deve considerare i metodi della super-classe
			decOffset = type.allMethods.size();
		
		for (MethodNode m : n.methods) {
			
			if (locals.contains(m.id)) { 	// OTTIMIZZAZIONE: campi e metodi non possono essere dichiarati 2 volte in una stessa classe
				System.out.println("Method id " + m.id + " at line "+ m.getLine() +" already declared");
				stErrors++;
				continue;
			} 
			
			// check overriding: se è un overriding di un NON MethodTypeNode skippo il metodo direttamente
			boolean overriding=false;
			STentry superMethodEntry = null;
			if(vt.containsKey(m.id)) {		// se il metodo è già in vt allora verifico se è possibile fare override
				
				superMethodEntry = vt.get(m.id);
				
				if (!(superMethodEntry.type instanceof MethodTypeNode))	// non consento overriding di un super-field in questo caso
				{
					System.out.println("Overriding with method " + m.id + " at line "+ m.getLine() +" not allowed");
					stErrors++;
					continue;
				}
				
				overriding=true;
			}
			
			locals.add(m.id);
			
			visit(m);
			
			if (overriding)
				type.allMethods.set(m.offset, ((MethodTypeNode)m.getType()).fun);	
			else
				type.allMethods.add(((MethodTypeNode)m.getType()).fun);	
		}
		
		//rimuovere la hashmap corrente poiche' esco dallo scope               
		symTable.remove(nestingLevel--);
		decOffset=prevNLDecOffset; 
		return null;
	}
	
	@Override
	public Void visitNode(MethodNode n) {
		if (print) printNode(n, n.id);
		
		Map<String, STentry> hm = symTable.get(nestingLevel);
		List<TypeNode> parTypes = new ArrayList<>();
		
		for (ParNode par : n.parlist) 
		{
			//TODO: check reftype exists
			parTypes.add(par.getType());
		}
		
		n.setType(new MethodTypeNode(new ArrowTypeNode(parTypes, n.retType)));
		
		visit(n.getType()); 										//MOD: verifico se è coinvolto un tipo RefType e in tal caso verifico la dichiarazione della classe a cui si riferisce
		
		STentry entry = null;
		
		if(hm.containsKey(n.id)) {									// overriding - il controllo circa la possibilità è già fatto da ClassNode
			STentry superMethodEntry = hm.get(n.id);
			n.offset=superMethodEntry.offset;						// uso l'offset precedente	
		} else {
			n.offset=decOffset++;									// gli offset dei (nuovi) metodi vengono incrementati
		}
		
		entry = new STentry(nestingLevel, n.getType(), n.offset); 
		
		//inserimento di ID nella symtable
		hm.put(n.id, entry); 
		
		//creare una nuova hashmap per la symTable
		nestingLevel++;
		Map<String, STentry> hmn = new HashMap<>();
		symTable.add(hmn);
		
		int parOffset=1;
		for (ParNode par : n.parlist) {
			if (hmn.put(par.id, new STentry(nestingLevel,par.getType(),parOffset++)) != null) {
				System.out.println("Par id " + par.id + " at line "+ n.getLine() +" already declared");
				stErrors++;
			}
		}
		
		int prevNLDecOffset=decOffset; // stores counter for offset of declarations at previous nesting level 
		decOffset=-2;
		for (Node dec : n.declist) visit(dec);
		visit(n.exp);
		//rimuovere la hashmap corrente poiche' esco dallo scope               
		symTable.remove(nestingLevel--);
		decOffset=prevNLDecOffset; // restores counter for offset of declarations at previous nesting level 
		return null;
	}
	
	@Override
	public Void visitNode(ClassCallNode n) {	// simile a callNode ma: refID.methodID(arg1,arg2)
		if (print) printNode(n);
		
		STentry entry = stLookup(n.refID);
		if (entry == null) {
			System.out.println(n.refID + " at line "+ n.getLine() + " not declared");
			stErrors++;
		} else {
			n.entry = entry;
			n.nl = nestingLevel;
			
			if(!(n.entry.type instanceof RefTypeNode)) {								//check se type presente (classe declared)
				System.out.println("Cannot call " + n.refID + "." + n.methodID + "() at line " + n.getLine() + ": class undefined");
				stErrors++;
			} else if (!classTable.containsKey(((RefTypeNode)n.entry.type).id)) { 		//check esistenza classe RefTypeNode.id
				System.out.println("Cannot call " + n.refID + "." + n.methodID + "() at line " + n.getLine() + ": class " + ((RefTypeNode)n.entry.type).id + " not declared");
				stErrors++;
			} else {
				RefTypeNode refType = (RefTypeNode)n.entry.type;
				if(!classTable.get(refType.id).containsKey(n.methodID)) {
					System.out.println("Cannot call " + n.refID + "." + n.methodID + "() at line "+ n.getLine() + ": method "+ n.methodID + " not declared in class " + refType.id);
					stErrors++;
				} else
					n.methodEntry = classTable.get(refType.id).get(n.methodID);
			}
			
			for (Node arg : n.arglist) visit(arg);
		}
		return null;
	}
	
	@Override
	public Void visitNode(NewNode n) {				// simile a CallNode
		if (print) printNode(n);
		
		if( !classTable.containsKey(n.id) )			// n.id è sempre l'ID di una classe - quindi verifico direttamente in classTable
		{
			System.out.println("Class " + n.id + " at line "+ n.getLine() + " not declared");
			stErrors++;
		}
		else
		{
			n.entry = symTable.get(0).get(n.id);	// vado a prendere l'entry della classe direttamente nell'ambiente globale
			n.nl = nestingLevel;
		}
		
		for (Node arg : n.arglist) visit(arg);
		return null;
	}
	
	@Override
	public Void visitNode(EmptyNode n) {
		if(print) printNode(n);
		return null;
	}
	
	// MOD: nelle dichiarazioni visito i tipi in modo che se arrivo ad un RefTypeNode posso verificare l'esistenza della classe
	
	@Override
	public Void visitNode(IntTypeNode n) {
		if(print) printNode(n);
		return null;
	}
	
	@Override
	public Void visitNode(BoolTypeNode n) {
		if(print) printNode(n);
		return null;
	}
	
	@Override
	public Void visitNode(ArrowTypeNode n) {
		if(print) printNode(n);
		
		for (int i=0;i<n.parlist.size();i++)
		{
			visit(n.parlist.get(i));
		}
		visit(n.ret);
		
		return null;
	}
	
	@Override
	public Void visitNode(MethodTypeNode n) {
		if(print) printNode(n);
		
		visit(n.fun);
		
		return null;
	}
	
	@Override
	public Void visitNode(RefTypeNode n) {
		if(print) printNode(n);
		
		String c = n.id;
		if(!(classTable.containsKey(c))) {
			System.out.println("Type " +c+ " not declared at line "+ n.getLine());
			stErrors++;
		}
		return null;
	}
}
