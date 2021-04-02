package compiler;

import compiler.AST.*;
import compiler.exc.*;
import compiler.lib.*;

import static compiler.TypeRels.*;

//visitNode(n) fa il type checking di un Node n e ritorna:
//- per una espressione, il suo tipo (oggetto BoolTypeNode o IntTypeNode)
//- per una dichiarazione, "null"; controlla la correttezza interna della dichiarazione
//(- per un tipo: "null"; controlla che il tipo non sia incompleto) 
//
//visitSTentry(s) ritorna, per una STentry s, il tipo contenuto al suo interno
public class TypeCheckEASTVisitor extends BaseEASTVisitor<TypeNode,TypeException> {

	TypeCheckEASTVisitor() { super(true); } // enables incomplete tree exceptions 
	TypeCheckEASTVisitor(boolean debug) { super(true,debug); } // enables print for debugging

	//checks that a type object is visitable (not incomplete) 
	private TypeNode ckvisit(TypeNode t) throws TypeException {
		visit(t);
		return t;
	} 
	
	@Override
	public TypeNode visitNode(ProgLetInNode n) throws TypeException {
		if (print) printNode(n);
		for (Node dec : n.declist)
			try {
				visit(dec);
			} catch (IncomplException e) {
				
			} catch (TypeException e) {
				System.out.println("Type checking error in a declaration: " + e.text);
			}
		return visit(n.exp);
	}

	@Override
	public TypeNode visitNode(ProgNode n) throws TypeException {
		if (print) printNode(n);
		return visit(n.exp);
	}

	@Override
	public TypeNode visitNode(FunNode n) throws TypeException {
		if (print) printNode(n,n.id);
		for (Node dec : n.declist)
			try {
				visit(dec);
			} catch (IncomplException e) {
				
			} catch (TypeException e) {
				System.out.println("Type checking error in a declaration: " + e.text);
			}
		
		//if (n.retType==null)
		//	throw new TypeException("Undeclared type for ret type for function " + n.id,n.getLine()); //MOD: se il type � un ID che non esiste blocco tutto
		//else 
		if ( !isSubtype(visit(n.exp),ckvisit(n.retType)) ) 
			throw new TypeException("Wrong return type for function " + n.id,n.getLine());
		return null;
	}

	@Override
	public TypeNode visitNode(VarNode n) throws TypeException {
		if (print) printNode(n,n.id);
		
		//if (n.getType()==null)
		//	throw new TypeException("Undeclared type for variable " + n.id,n.getLine()); //MOD: se il type � un ID che non esiste blocco tutto
		//else 
		if ( !isSubtype(visit(n.exp),ckvisit(n.getType())) )
			throw new TypeException("Incompatible value for variable " + n.id,n.getLine());
		return null;
	}

	@Override
	public TypeNode visitNode(PrintNode n) throws TypeException {
		if (print) printNode(n);
		return visit(n.exp);
	}

	@Override
	public TypeNode visitNode(IfNode n) throws TypeException {
		if (print) printNode(n);
		if ( !(isSubtype(visit(n.cond), new BoolTypeNode())) )
			throw new TypeException("Non boolean condition in if",n.getLine());
		TypeNode t = visit(n.th);
		TypeNode e = visit(n.el);
		
		TypeNode r = LowestCommonAncestor(t, e);		//MOD: OTTIMIZZAZIONE
		if (r!=null)
			return r;

		throw new TypeException("Incompatible types in then-else branches",n.getLine());
	}

	@Override
	public TypeNode visitNode(EqualNode n) throws TypeException {
		if (print) printNode(n);
		TypeNode l = visit(n.left);
		TypeNode r = visit(n.right);
		
		if (l instanceof ArrowTypeNode || r instanceof ArrowTypeNode)				//MOD (HO)
			throw new TypeException("ArrowTypes are not comparable",n.getLine());
		
		if ( !(isSubtype(l, r) || isSubtype(r, l)) )
			throw new TypeException("Incompatible types in equal",n.getLine());
		return new BoolTypeNode();
	}

	@Override
	public TypeNode visitNode(TimesNode n) throws TypeException {
		if (print) printNode(n);
		if ( !(isSubtype(visit(n.left), new IntTypeNode())
				&& isSubtype(visit(n.right), new IntTypeNode())) )
			throw new TypeException("Non integers in multiplication",n.getLine());
		return new IntTypeNode();
	}

	@Override
	public TypeNode visitNode(PlusNode n) throws TypeException {
		if (print) printNode(n);
		if ( !(isSubtype(visit(n.left), new IntTypeNode())
				&& isSubtype(visit(n.right), new IntTypeNode())) )
			throw new TypeException("Non integers in sum",n.getLine());
		return new IntTypeNode();
	}

	@Override
	public TypeNode visitNode(CallNode n) throws TypeException {
		if (print) printNode(n,n.id);
		
		TypeNode t = visit(n.entry); 
		if ( t instanceof MethodTypeNode )	//MOD (OO) chiamata di un metodo dall'interno di una classe
			t=((MethodTypeNode)t).fun;
		
		if ( !(t instanceof ArrowTypeNode) )
			throw new TypeException("Invocation of a non-function "+n.id,n.getLine());
		
		ArrowTypeNode at = (ArrowTypeNode) t;
		if ( !(at.parlist.size() == n.arglist.size()) )
			throw new TypeException("Wrong number of parameters in the invocation of "+n.id,n.getLine());
		for (int i = 0; i < n.arglist.size(); i++)
			if ( !(isSubtype(visit(n.arglist.get(i)),at.parlist.get(i))) )
				throw new TypeException("Wrong type for "+(i+1)+"-th parameter in the invocation of "+n.id,n.getLine());
		return at.ret;
	}

	@Override
	public TypeNode visitNode(IdNode n) throws TypeException {
		if (print) printNode(n,n.id);
		TypeNode t = visit(n.entry); 
		
		//if (t instanceof ArrowTypeNode)														//MOD (HO) ora è possibile che un id sia una fun
		//	throw new TypeException("Wrong usage of function identifier " + n.id,n.getLine());

		if (t instanceof ClassTypeNode)															//MOD (OO)
			throw new TypeException("Wrong usage of class identifier " + n.id,n.getLine());
		if (t instanceof MethodTypeNode)														
			throw new TypeException("Wrong usage of method identifier " + n.id,n.getLine());
		
		return t;
	}

	@Override
	public TypeNode visitNode(BoolNode n) {
		if (print) printNode(n,n.val.toString());
		return new BoolTypeNode();
	}

	@Override
	public TypeNode visitNode(IntNode n) {
		if (print) printNode(n,n.val.toString());
		return new IntTypeNode();
	}

// gestione tipi incompleti	(se lo sono lancia eccezione)
	
	@Override
	public TypeNode visitNode(ArrowTypeNode n) throws TypeException {
		if (print) printNode(n);
		for (Node par: n.parlist) visit(par);
		visit(n.ret,"->"); //marks return type
		return null;
	}

	@Override
	public TypeNode visitNode(BoolTypeNode n) {
		if (print) printNode(n);
		return null;
	}

	@Override
	public TypeNode visitNode(IntTypeNode n) {
		if (print) printNode(n);
		return null;
	}

// STentry (ritorna campo type)

	@Override
	public TypeNode visitSTentry(STentry entry) throws TypeException {
		if (print) printSTentry("type");
		return ckvisit(entry.type); 
	}
	
	//MOD: NEW VISITS
	
	// OPERATORS
	
	@Override
	public TypeNode visitNode(MinusNode n) throws TypeException {	//come PlusNode
		if (print) printNode(n);
		
		if ( !(isSubtype(visit(n.left), new IntTypeNode())
				&& isSubtype(visit(n.right), new IntTypeNode())) )
			throw new TypeException("Non integers in minus",n.getLine());
		
		return new IntTypeNode();
	}
	
	@Override
	public TypeNode visitNode(DivNode n) throws TypeException {		//come TimesNode
		if (print) printNode(n);
		
		if ( !(isSubtype(visit(n.left), new IntTypeNode())
				&& isSubtype(visit(n.right), new IntTypeNode())) )
			throw new TypeException("Non integers in division",n.getLine());
		
		return new IntTypeNode();
	}
	
	@Override
	public TypeNode visitNode(GreaterEqualNode n) throws TypeException {	//come EqualNode
		if (print) printNode(n);
		
		TypeNode l = visit(n.left);
		TypeNode r = visit(n.right);
		
		if (l instanceof ArrowTypeNode || r instanceof ArrowTypeNode)				// check introdotto con supporto a HO
			throw new TypeException("ArrowTypes are not comparable",n.getLine());
		
		if ( !(isSubtype(l, r) || isSubtype(r, l)) )
			throw new TypeException("Incompatible types in greater than equal",n.getLine());
		
		return new BoolTypeNode();
	}
	
	@Override
	public TypeNode visitNode(LessEqualNode n) throws TypeException {	//come EqualNode
		if (print) printNode(n);
		
		TypeNode l = visit(n.left);
		TypeNode r = visit(n.right);
		
		if (l instanceof ArrowTypeNode || r instanceof ArrowTypeNode)				// check introdotto con supporto a HO
			throw new TypeException("ArrowTypes are not comparable",n.getLine());
		
		if ( !(isSubtype(l, r) || isSubtype(r, l)) )
			throw new TypeException("Incompatible types in less than equal",n.getLine());
		
		return new BoolTypeNode();
	}
	
	@Override
	public TypeNode visitNode(NotNode n) throws TypeException {
		if (print) printNode(n);
		
		if ( !isSubtype(visit(n.exp), new BoolTypeNode()) )
			throw new TypeException("Non boolean in not",n.getLine());
		
		return new BoolTypeNode();
	}
	
	@Override
	public TypeNode visitNode(OrNode n) throws TypeException {
		if (print) printNode(n);
		
		if ( !(isSubtype(visit(n.left), new BoolTypeNode()) && isSubtype(visit(n.right), new BoolTypeNode())) )
			throw new TypeException("Incompatible types in or",n.getLine());
		
		return new BoolTypeNode();
	}
	
	@Override
	public TypeNode visitNode(AndNode n) throws TypeException {
		if (print) printNode(n);
		
		if ( !(isSubtype(visit(n.left), new BoolTypeNode()) && isSubtype(visit(n.right), new BoolTypeNode())) )
			throw new TypeException("Incompatible types in and",n.getLine());
		
		return new BoolTypeNode();
	}
	
	// OBJECT ORIENTED
	
	@Override
	public TypeNode visitNode(ClassNode n) throws TypeException {
		if (print) printNode(n);
		
		if(n.superEntry!=null)									// la classe estende un'altra classe che � gi� dichiarata (check fatto da symbol table visitor)
			TypeRels.superType.put(n.id, n.superID);			// memorizzo la relazione tra le classi per permettere type-check dei RefType
		
		for (Node met : n.methods)
			visit(met);
		
		if(n.superEntry!=null) {								// check overriding types di campi e metodi								
			
			ClassTypeNode parentCT = (ClassTypeNode)n.superEntry.type;
			
			for(FieldNode f : n.fields) {						// OTTIMIZZAZIONE: ciclo i campi e verifico sulla super-classe se c'� stato override corretto
				int superPos = -f.offset-1;
				if (superPos >= 0 && superPos < parentCT.allFields.size()) {
					if ( !(isSubtype(f.getType(), parentCT.allFields.get(superPos))) ) 
						throw new TypeException("Wrong overriding of field " + f.id, n.getLine());
				}
			}
			
			for(MethodNode m : n.methods) {						// OTTIMIZZAZIONE: ciclo i metodi e verifico sulla super-classe se c'� stato override corretto
				int superPos = m.offset;						
				if (superPos >= 0 && superPos < parentCT.allMethods.size()) {
					if ( !(isSubtype(((MethodTypeNode)m.getType()).fun, parentCT.allMethods.get(superPos))) )
						throw new TypeException("Wrong overriding of method " + m.id, n.getLine());
				}
			}
		}
		return null;
	}
	
	@Override
	public TypeNode visitNode(MethodNode n) throws TypeException {		// come FunNode
		if (print) printNode(n,n.id);
		
		for (Node dec : n.declist)
		{
			try {
				visit(dec);
			} catch (IncomplException e) {
				
			} catch (TypeException e) {
				System.out.println("Type checking error in a declaration: " + e.text);
			}
		}
		
		if ( !isSubtype(visit(n.exp),ckvisit(n.retType)) ) 				// il tipo dell'espressione deve essere sotto-tipo della dichiarazione di ritorno
			throw new TypeException("Wrong return type for method " + n.id,n.getLine());
		
		return null;
	}
	
	@Override
	public TypeNode visitNode(ClassCallNode n) throws TypeException {	// simile a CallNode - chiamata refID.methodID()
		if (print) printNode(n,n.refID + "." + n.methodID);
		
		TypeNode t = visit(n.methodEntry); 
		if ( !(t instanceof MethodTypeNode) )							// deve essere un metodo
			throw new TypeException("Invocation of a non-method "+n.methodID,n.getLine());
		
		ArrowTypeNode at = ((MethodTypeNode)t).fun;						// recupero l'arrow type

		if ( !(at.parlist.size() == n.arglist.size()) )
			throw new TypeException("Wrong number of parameters in the invocation of "+n.methodID,n.getLine());
		for (int i = 0; i < n.arglist.size(); i++)
			if ( !(isSubtype(visit(n.arglist.get(i)),at.parlist.get(i))) )
				throw new TypeException("Wrong type for "+(i+1)+"-th parameter in the invocation of "+n.methodID,n.getLine());
		return at.ret;
	}
	
	@Override
	public TypeNode visitNode(NewNode n) throws TypeException {			// simile a CallNode - gli argomenti sono i campi della classe
		if (print) printNode(n,n.id);
		
		TypeNode t = visit(n.entry); 
		if ( !(t instanceof ClassTypeNode) )
			throw new TypeException("Instancing of a non-class "+n.id,n.getLine());
		
		ClassTypeNode ct = (ClassTypeNode) t;
		if ( !(ct.allFields.size() == n.arglist.size()) )
			throw new TypeException("Wrong number of parameters to instance an object of class "+n.id,n.getLine());
		for (int i = 0; i < n.arglist.size(); i++)
			if ( !(isSubtype(visit(n.arglist.get(i)),ct.allFields.get(i))) )
				throw new TypeException("Wrong type for "+(i+1)+"-th parameter to instance an object of class "+n.id,n.getLine());
		
		return new RefTypeNode(n.id);
	}
	
	@Override
	public TypeNode visitNode(EmptyNode n) {
		if (print) printNode(n);
		return new EmptyTypeNode();
	}
	
	public TypeNode visitNode(ClassTypeNode n) {
		if(print) printNode(n);
		return null;
	}
	
	@Override
	public TypeNode visitNode(MethodTypeNode n) throws TypeException{
		if (print) printNode(n);
		visit(n.fun);
		return null;
	}
	
	@Override
	public TypeNode visitNode(RefTypeNode n) throws TypeException {
		if(print) printNode(n,n.id);
		return null;
	}
}