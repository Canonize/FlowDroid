package soot.jimple.infoflow.android.iccta;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import soot.Body;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.Stmt;
import soot.jimple.infoflow.android.iccta.Ic3Data.Application.Component;

public class Ic3Provider implements IccLinkProvider {
	private final Logger logger = LoggerFactory.getLogger(getClass());

	private String ic3Model = null;

	public Ic3Provider(String ic3Model) {
		this.ic3Model = ic3Model;
	}

	@Override
	public List<IccLink> getIccLinks() {
		List<IccLink> iccLinks = new ArrayList<IccLink>();

		App app = Ic3ResultLoader.load(ic3Model);

		if (null == app) {
			logger.error("[IccTA] %s is not a valid IC3 model", ic3Model);
			return iccLinks;
		}

		Set<Intent> intents = app.getIntents();
		// List<Intent> intents = app.getIntents();
		// //+++++
		// System.out.println("=============app============");
		// app.dump();
		// System.out.println("==========\n");
		// System.out.println("intents size : " + intents.size()+"\n");
		int i=0;
		for (Intent intent : intents) {
			//+++++
			i++;
			// System.out.println(i+" intent : "+intent.toString());
			// System.out.println("----------------------------");
			// System.out.println(intent.getLoggingPoint().dump());
			// System.out.println("\n");

			if (intent.isImplicit()) {
				if (null == intent.getAction()) {
					continue;
				}
				List<Component> targetedComps = intent.resolve(app.getComponentList());

				for (Component targetComp : targetedComps) {
					if (!availableTargetedComponent(intent.getApp(), targetComp.getName())) {
						continue;
					}

					SootMethod fromSM = Scene.v().grabMethod(intent.getLoggingPoint().getCallerMethodSignature());
					Stmt fromU = linkWithTarget(fromSM, intent.getLoggingPoint().getStmtSequence());
					//+++
					SootMethod contextM = Scene.v().grabMethod(intent.getLoggingPoint().getSourceClass().split("#")[0]);

					Stmt contextU = linkWithTarget(contextM, Integer.parseInt(intent.getLoggingPoint().getSourceClass().split("#")[1]));
					
					IccLink iccLink = new IccLink(fromSM, fromU, Scene.v().getSootClassUnsafe(targetComp.getName()), contextM, contextU);
					iccLink.setExit_kind(targetComp.getKind().name());

					iccLinks.add(iccLink);
				}
			} else {
				String targetCompName = intent.getComponentClass();
				if (!availableTargetedComponent(intent.getApp(), targetCompName)) {
					//+++++
					System.out.println("can not found targetCompName : "+targetCompName+"\n");
					continue;
				}

				SootMethod fromSM = Scene.v().grabMethod(intent.getLoggingPoint().getCallerMethodSignature());
				//+++++
				// System.out.println("fromSM : "+fromSM.toString());

				if (fromSM != null) {
					Stmt fromU = linkWithTarget(fromSM, intent.getLoggingPoint().getStmtSequence());
					//+++
					SootMethod contextM = Scene.v().grabMethod(intent.getLoggingPoint().getSourceClass().split("#")[0]);

					Stmt contextU = linkWithTarget(contextM, Integer.parseInt(intent.getLoggingPoint().getSourceClass().split("#")[1]));
					
					IccLink iccLink = new IccLink(fromSM, fromU, Scene.v().getSootClassUnsafe(targetCompName), contextM, contextU);
					//+++++
					// System.out.println(iccLink.toString()+"\n\n");

					for (Component comp : intent.getApp().getComponentList()) {
						if (comp.getName().equals(targetCompName)) {
							iccLink.setExit_kind(comp.getKind().name());
						}
					}

					iccLinks.add(iccLink);
				}
			}
		}
		//+++++
		System.out.println("iccbot-intent count : "+i);
		return iccLinks;
	}

	private Stmt linkWithTarget(SootMethod fromSM, int stmtIdx) {
		Body body = fromSM.retrieveActiveBody();

		int i = 0;
		for (Iterator<Unit> iter = body.getUnits().snapshotIterator(); iter.hasNext();) {
			Stmt stmt = (Stmt) iter.next();

			if(stmt.toString().contains("redirector")){
				continue;
			}
			
			if (i == stmtIdx) {
			//+++++
			// System.out.println(stmtIdx+ " : "+ stmt+"\n");

			return stmt;
			}
			i++;
		}
		return null;
	}

	private boolean availableTargetedComponent(App app, String targetedComponentName) {
		for (Component comp : app.getComponentList()) {
			if (comp.getName().equals(targetedComponentName)) {
				return true;
			}
		}

		return false;
	}
}
