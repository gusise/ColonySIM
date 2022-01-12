package colony.behaviours;

import java.util.List;

import jade.core.Agent;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.FIPAAgentManagement.DFAgentDescription;

public class PrintContacts extends OneShotBehaviour {

	DFAgentDescription contact;
	String s_type;
	List<DFAgentDescription> contactList;
	int st = 0;

	public PrintContacts(Agent a, DFAgentDescription contact, String s_type) {
		super(a);
		this.contact = contact;
		this.s_type = s_type;
		this.st = 1;
	}

	public PrintContacts(Agent a, List<DFAgentDescription> contactList, String s_type) {
		super(a);
		this.contactList = contactList;
		this.s_type = s_type;
		this.st = 2;
	}

	public void action() {
		switch (st) {
		case 1:
			if (contact != null) {
				System.out.println(myAgent.getLocalName() + " <- " + s_type + ": " + contact.getName().getLocalName());
			} else {
				System.out.println(myAgent.getLocalName() + " <- found no " + s_type);
			}
			break;
		case 2:
			if (!contactList.isEmpty()) {
				String known = " ";
				for (DFAgentDescription a : contactList)
					known = known + a.getName().getLocalName() + ", ";
				System.out.println(myAgent.getLocalName() + " <- " + s_type + ": " + known);
			} else {
				System.out.println(myAgent.getLocalName() + " <- found no " + s_type);
			}
			break;
		default:
			break;
		}

	}

}
