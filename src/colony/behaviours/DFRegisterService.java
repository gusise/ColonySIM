package colony.behaviours;

import jade.core.Agent;
import jade.core.behaviours.SimpleBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

public class DFRegisterService extends SimpleBehaviour {
	ServiceDescription sd;

	public DFRegisterService(Agent a, String type) {
		super(a);
		ServiceDescription sd = new ServiceDescription();
		sd.setType(type);
		sd.setName(myAgent.getLocalName());
	}

	public void action() {
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(myAgent.getAID());
		dfd.addServices(sd);

		try {
			// DFService.deregister(this);
			DFService.register(myAgent, dfd);
		} catch (FIPAException fe) {
			fe.printStackTrace();
		}
	}

	public boolean done() {
		return true;
	}

}
