package soot.jimple.infoflow.android.iccta;

import java.util.Collections;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import soot.Body;
import soot.Local;
import soot.LocalGenerator;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.RefType;
import soot.Value;
import soot.ValueBox;
import soot.Type;
import soot.RefType;
import soot.VoidType;
import soot.Modifier;
import soot.javaToJimple.LocalGenerator;
import soot.jimple.Jimple;
import soot.jimple.JimpleBody;
import soot.jimple.Stmt;
import soot.jimple.infoflow.android.entryPointCreators.components.ComponentEntryPointCollection;
import soot.jimple.infoflow.entryPointCreators.SimulatedCodeElementTag;
import soot.jimple.infoflow.handlers.PreAnalysisHandler;
import soot.util.Chain;
import soot.util.HashMultiMap;
import soot.util.MultiMap;

public class IccInstrumenter implements PreAnalysisHandler {

	protected final Logger logger = LoggerFactory.getLogger(getClass());

	protected final String iccModel;
	protected final SootClass dummyMainClass;
	protected final ComponentEntryPointCollection componentToEntryPoint;

	protected IccRedirectionCreator redirectionCreator = null;
	//+++
	private final RefType MESSAGE_TYPE = RefType.v("android.os.Message");
	private static int num = 0;

	protected final SootMethod smMessengerSend;
	protected final Set<SootMethod> processedMethods = new HashSet<>();
	protected final MultiMap<Body, Unit> instrumentedUnits = new HashMultiMap<>();

	public IccInstrumenter(String iccModel, SootClass dummyMainClass,
			ComponentEntryPointCollection componentToEntryPoint) {
		this.iccModel = iccModel;
		this.dummyMainClass = dummyMainClass;
		this.componentToEntryPoint = componentToEntryPoint;

		// Fetch some Soot methods
		smMessengerSend = Scene.v().grabMethod("<android.os.Messenger: void send(android.os.Message)>");
	}

	@Override
	public void onBeforeCallgraphConstruction() {
		logger.info("[IccTA] Launching IccTA Transformer...");

		logger.info("[IccTA] Loading the ICC Model...");
		Ic3Provider provider = new Ic3Provider(iccModel);
		List<IccLink> iccLinks = provider.getIccLinks();
		logger.info("[IccTA] ...End Loading the ICC Model");

		// Create the redirection creator
		if (redirectionCreator == null)
			redirectionCreator = new IccRedirectionCreator(dummyMainClass, componentToEntryPoint);
		else
			redirectionCreator.undoInstrumentation();

		logger.info("[IccTA] Lauching ICC Redirection Creation...");
		//+++++
		int cnt =1;
		for (IccLink link : iccLinks) {
			if (link.fromU == null) {
				continue;
			}
			//+++++
			System.out.println(cnt+" : "+link.toString());
			redirectionCreator.redirectToDestination(link);
			System.out.println("\n");
			cnt++;
		}

		// Remove any potential leftovers from the last last instrumentation
		undoInstrumentation();

		// Instrument the messenger class
		instrumentMessenger();

		// Remove data that is no longer needed
		processedMethods.clear();

		logger.info("[IccTA] ...End ICC Redirection Creation");
	}

	/**
	 * Removes all units generated through instrumentation
	 */
	protected void undoInstrumentation() {
		for (Body body : instrumentedUnits.keySet()) {
			for (Unit u : instrumentedUnits.get(body)) {
				body.getUnits().remove(u);
			}
		}
		instrumentedUnits.clear();
	}

	protected void instrumentMessenger() {
		logger.info("Launching Messenger Transformer...");

		Chain<SootClass> applicationClasses = Scene.v().getApplicationClasses();
		Map<String,String> handlerClass= new HashMap<>();
		Map<String,String> handlerInner= new HashMap<>();

		for (Iterator<SootClass> iter = applicationClasses.snapshotIterator(); iter.hasNext();) {
			SootClass sootClass = iter.next();

			// We copy the list of methods to emulate a snapshot iterator which
			// doesn't exist for methods in Soot
			List<SootMethod> methodCopyList = new ArrayList<>(sootClass.getMethods());
			for (SootMethod sootMethod : methodCopyList) {
				if (sootMethod.isConcrete()) {
					final Body body = sootMethod.retrieveActiveBody();				

					// Mark the method as processed
					if (!processedMethods.add(sootMethod))
						continue;

					for (Iterator<Unit> unitIter = body.getUnits().snapshotIterator(); unitIter.hasNext();) {
						Unit unit = unitIter.next();
						Stmt stmt = (Stmt) unit;
						//+++ collect handler fields signature	
						if (stmt.containsFieldRef() && stmt.getFieldRef().getType().toString().contains("android.os.Handler")) {
							if(stmt.getUseBoxes().size() > 1 && handlerInner.get(stmt.getFieldRef().getField().getSignature()) == null)
								handlerInner.put(stmt.getFieldRef().getField().getSignature(),stmt.getUseBoxes().get(1).getValue().getType().toString());
							// use value as locator
							handlerClass.put(sootClass.getName() + ":" + stmt.getDefBoxes().get(0).getValue(),stmt.getFieldRef().getField().getSignature());
						}
							
						if(stmt.containsInvokeExpr() && stmt.getInvokeExpr().getMethodRef().getSignature().contains("android.os.Handler")) {
							//Soot has a access$XXX method that returns private fields in outer class
							if(stmt.getInvokeExpr().getMethodRef().getName().contains("access")) {
								//System.out.println("Syntheic: "+stmt.toString());
								Body b = stmt.getInvokeExpr().getMethod().retrieveActiveBody();
								for (Iterator<Unit> u = b.getUnits().iterator(); u.hasNext();) {
									Stmt s = (Stmt) u.next();
									//if the access method return a handler, collect its signature
									if(s.containsFieldRef() && s.getFieldRef().getType().toString().contains("android.os.Handler"))
									{
										if(!stmt.getDefBoxes().isEmpty())
											handlerClass.put(sootClass.getName() + ":" + stmt.getDefBoxes().get(0).getValue(),s.getFieldRef().getField().getSignature());
										//System.out.println("++"+handlerClass.toString());
									}
								}
							}
						}													
					}

					body.validate();
				}

			}

								
		}
		//System.out.println(" @@ "+handlerClass.toString());
		//System.out.println(" @@ "+handlerInner.toString());
		//instrument the outerclass
		for(String sc : handlerClass.keySet())
			if(sc!=null)
				generateSendMessage(sc.split(":")[0],handlerClass,handlerInner);

	}

	public void generateSendMessage(String sc,Map<String,String> handlerClass,Map<String,String> handlerInner) {
		
		if(sc == null || handlerClass.isEmpty() || handlerInner.isEmpty())
			return;
		//System.out.println("--0: "+ sc);
		SootClass sootClass = Scene.v().getSootClass(sc);
		List<SootMethod> methodCopyList = new ArrayList<>(sootClass.getMethods());
		for (SootMethod sootMethod : methodCopyList) {
			if (sootMethod.isConcrete()) {
				final Body body = sootMethod.retrieveActiveBody();
				final LocalGenerator lg = Scene.v().createLocalGenerator(body);
				
				for (Iterator<Unit> unitIter = body.getUnits().snapshotIterator(); unitIter.hasNext();) {
					Unit unit = unitIter.next();
					Stmt stmt = (Stmt) unit;
					if (stmt.containsInvokeExpr()) {
						SootMethod callee = stmt.getInvokeExpr().getMethod();
						
						// For sendMessage(), we directly call the respective handler.handleMessage()
						if (callee == smMessengerSend || callee == Scene.v().grabMethod("<android.os.Handler: boolean sendMessage(android.os.Message)>")) {
							//collect the value for sendMessage()
							String hc = handlerClass.get(sootClass.getName() + ":" + stmt.getInvokeExpr().getUseBoxes().get(1).getValue().toString());
							//System.out.println("--1: "+hc);
							Set<SootClass> handlers = MessageHandler.v().getAllHandlers();								
							soot.javaToJimple.InitialResolver.v().getNextPrivateAccessCounter();
							for (SootClass handler : handlers) {
								// matching the handler and its signature	
								if(hc != null && handlerInner.get(hc) == handler.getName()) {
									Local handlerLocal = lg.generateLocal(handler.getType());													
									SootMethod hmMethod = handler.getMethod("void handleMessage(android.os.Message)");
									Unit callHMU = Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr(
											handlerLocal, hmMethod.makeRef(), stmt.getInvokeExpr().getArg(0)));
									callHMU.addTag(SimulatedCodeElementTag.TAG);
									body.getUnits().insertAfter(callHMU, stmt);
									instrumentedUnits.put(body, callHMU);
									//System.out.println("--4: "+callHMU.toString());
									break;
								}			
							}
						}
					}	
												
					
				}

				body.validate();
			}
		}
		
	}

	@Override
	public void onAfterCallgraphConstruction() {
		//
	}
}
