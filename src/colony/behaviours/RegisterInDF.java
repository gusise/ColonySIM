package colony.behaviours;

import colony.ColonyVocabulary;
import jade.core.Agent;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

public class RegisterInDF extends OneShotBehaviour {

	String service_type;
	String owner_colony = null;

	public RegisterInDF(Agent a, String s_type) {
		super(a);
		this.service_type = s_type;
	}

	public RegisterInDF(Agent a, String s_type, String myColony) {
		super(a);
		this.service_type = s_type;
		this.owner_colony = myColony;
	}

	public void action() {

		ServiceDescription sd = new ServiceDescription();
		sd.setName(myAgent.getName());
		sd.setType(service_type);
		if (owner_colony != null)
			sd.setOwnership(owner_colony);
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(myAgent.getAID());
		dfd.addServices(sd);
		try {
			DFAgentDescription[] dfds = DFService.search(myAgent, dfd);
			if (dfds.length > 0) {
				DFService.deregister(myAgent, dfd);
			}
			DFService.register(myAgent, dfd);
			System.out.println(myAgent.getLocalName() + " <- registered to DF.");
		} catch (Exception ex) {
			System.out.println("Failed registering with DF! Shutting down...");
			ex.printStackTrace();
			myAgent.doDelete();
		}
	}
}
