package compiler;

import compiler.AST.*;
import compiler.lib.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TypeRels {
	
	public static Map<String,String> superType = new HashMap<>();

	//MOD: HO
	// valuto se la funzione passata "a" è compatibile con la funzione richiesta come argomento "b" 
	// o in alternativa se la funzione che si vuole assegnare "a" è compatibile con la var "b"
	public static boolean arrowIsCompatible(ArrowTypeNode a, ArrowTypeNode b) {
		
		if(a.parlist.size() != b.parlist.size()) 
			return false;
		
		// covarianza dei return types: a.ret deve essere <= b.ret
		if(!isSubtype(a.ret, b.ret)) 
			return false;
		
		// contro-varianza sul tipo dei parametri: a.par_i >= b.par_i
		for(int i = 0; i < a.parlist.size(); i++) {
			if(!isSubtype(b.parlist.get(i), a.parlist.get(i))) 
				return false;
		}
		
		return true;
	}
	
	//MOD: OO
	// valuto se "a" estende "b"
	public static boolean isSubClass(RefTypeNode a, RefTypeNode b) {
		
		if (a.id.equals(b.id))
			return true;
		
		String sub = a.id;
		while(superType.containsKey(sub)) {
			sub = superType.get(sub);
			if(sub.equals(b.id))
				return true;
		}
		
		return false;
	}
	
	// valuta se il tipo "a" e' <= al tipo "b", dove "a" e "b" sono tipi di base: IntTypeNode o BoolTypeNode
	// NB: BoolTypeNode < IntTypeNode 
	public static boolean isSubtype(TypeNode a, TypeNode b) {
		
		if (a instanceof EmptyTypeNode && b instanceof RefTypeNode)
			return true;
		
		if (a instanceof RefTypeNode && b instanceof RefTypeNode)
			return isSubClass((RefTypeNode)a,(RefTypeNode)b);
		
		if (a instanceof MethodTypeNode && b instanceof MethodTypeNode)					// check per corretto overriding dei metodi
			return arrowIsCompatible(((MethodTypeNode)a).fun,((MethodTypeNode)b).fun);
		
		if (a instanceof ArrowTypeNode && b instanceof ArrowTypeNode)
			return arrowIsCompatible((ArrowTypeNode)a,(ArrowTypeNode)b);
					
		return a.getClass().equals(b.getClass()) || ((a instanceof BoolTypeNode) && (b instanceof IntTypeNode));
	}
	
	//MOD: OTTIMIZZAZIONE
	public static TypeNode LowestCommonAncestor(TypeNode a, TypeNode b) {
		
		// se uno dei 2 è EmptyTypeNode allora torna l'altro (deve comunque essere un RefTypeNode)
		if (a instanceof RefTypeNode && b instanceof EmptyTypeNode)
			return a;
		if (b instanceof RefTypeNode && a instanceof EmptyTypeNode)
			return b;
		
		// risalgo la catena delle estensioni di A e ad ogni passo verifico se isSubType(B, A-ancestor)
		if (a instanceof RefTypeNode && b instanceof RefTypeNode) {
			
			if(isSubtype(b, a)) 
				return a;
			
			RefTypeNode refA = (RefTypeNode)a;
			while ( superType.containsKey(refA.id) ) {
				refA = new RefTypeNode(superType.get(refA.id));
				
				if(isSubtype(b, refA)) 
					return refA;
			}
			
			return null;
		}
		
		//HO: ritorno un tipo Arrow comune ad a e b
		if (a instanceof ArrowTypeNode && b instanceof ArrowTypeNode) {
			ArrowTypeNode arrowA = (ArrowTypeNode) a;
			ArrowTypeNode arrowB = (ArrowTypeNode) b;
			
			if (arrowA.parlist.size() == arrowB.parlist.size()) {
				TypeNode retType = LowestCommonAncestor(arrowA.ret, arrowB.ret);	// co-varianza del tipo di ritorno
				
				if( retType != null) {
					List<TypeNode> parTypes = new ArrayList<>();
					for(int i = 0; i < arrowA.parlist.size(); i++) {	// per ogni parametro prendo il tipo subType dell'altro - contro-varianza del tipo dei parametri
						if(isSubtype(arrowA.parlist.get(i), arrowB.parlist.get(i)))
							parTypes.add(arrowA.parlist.get(i));
						else if(isSubtype(arrowB.parlist.get(i), arrowA.parlist.get(i)))
							parTypes.add(arrowB.parlist.get(i));
						else
							return null;
					}
					
					return new ArrowTypeNode(parTypes,retType);
				}
			}
			
			return null;
		}
		
		// IntTypeNode > BoolTypeNode
		if ((a instanceof IntTypeNode || a instanceof BoolTypeNode) && (b instanceof IntTypeNode || b instanceof BoolTypeNode))
		{
			if (a instanceof IntTypeNode || b instanceof IntTypeNode)
				return new IntTypeNode();
			else
				return new BoolTypeNode();
		}
		
		return null;
	}

}
