package soot.jimple.infoflow.android.iccta;

import soot.SootClass;
import soot.SootMethod;
import soot.Unit;

public class IccLink {

	protected final SootMethod fromSM;
	protected final Unit fromU;
	protected final SootClass destinationC;
	//+++
	protected final SootMethod contextM;
	protected final Unit contextU;
	protected String exit_kind;

	public IccLink(SootMethod fromSm, Unit fromU, SootClass destinationC, SootMethod contextM, Unit contextU) {
		this.fromSM = fromSm;
		this.fromU = fromU;
		this.destinationC = destinationC;
		//+++
		this.contextM = contextM;
		this.contextU = contextU;
	}

	@Override
	public String toString() {
		//+++
		return fromSM + " [" + fromU + "] " + destinationC;
	}

	public SootMethod getFromSM() {
		return fromSM;
	}

	public Unit getFromU() {
		return fromU;
	}

	public String getExit_kind() {
		return exit_kind;
	}

	public void setExit_kind(String exit_kind) {
		this.exit_kind = exit_kind;
	}

	public SootClass getDestinationC() {
		return destinationC;
	}

	//+++
	public SootMethod getcontextM() {
		return contextM;
	}

	public Unit getcontextU() {
		return contextU;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((destinationC == null) ? 0 : destinationC.hashCode());
		//+++
		result = prime * result + ((contextM == null) ? 0 : contextM.hashCode());
		result = prime * result + ((contextU == null) ? 0 : contextU.hashCode());
		result = prime * result + ((exit_kind == null) ? 0 : exit_kind.hashCode());
		result = prime * result + ((fromSM == null) ? 0 : fromSM.hashCode());
		result = prime * result + ((fromU == null) ? 0 : fromU.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		IccLink other = (IccLink) obj;
		if (destinationC == null) {
			if (other.destinationC != null)
				return false;
		} else if (!destinationC.equals(other.destinationC))
			return false;
		//+++
		if (contextM == null) {
			if (other.contextM != null)
				return false;
		} else if (!contextM.equals(other.contextM))
			return false;
			
		if (contextU == null) {
			if (other.contextU != null)
				return false;
		} else if (!contextU.equals(other.contextU))
			return false;	

		if (exit_kind == null) {
			if (other.exit_kind != null)
				return false;
		} else if (!exit_kind.equals(other.exit_kind))
			return false;
		if (fromSM == null) {
			if (other.fromSM != null)
				return false;
		} else if (!fromSM.equals(other.fromSM))
			return false;
		if (fromU == null) {
			if (other.fromU != null)
				return false;
		} else if (!fromU.equals(other.fromU))
			return false;
		return true;
	}

}
