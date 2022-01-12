package colony;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;

import javax.net.ssl.CertPathTrustManagerParameters;

import colony.Leader.GCAgent;
import colony.behaviours.RegisterInDF;
import colony.environment.MapGenerator;
import colony.environment.PersonalMap;
import colony.environment.PersonalMapLightWeight;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.SequentialBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

public class Enviro extends Agent implements ColonyVocabulary {

	Random rnd = new Random();
	MessageTemplate request = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
	MapGenerator mapgen;
	PersonalMap pMap;
	PersonalMapLightWeight map4msg;
	String myServiceType;
	Behaviour flusher;

	protected void setup() {

		// -------- BEHAVIOURS --------

		// register Agent to DF
		myServiceType = ENVIRONMENT;
		addBehaviour(new RegisterInDF(this, myServiceType));

		// start msg cleanup
		if (flusher == null) {
			flusher = new GCAgent(this, 10 * 1000);
			addBehaviour(flusher);
		}

		mapgen = new MapGenerator(12, 12, 16, 0, 3, 107);

		pMap = new PersonalMap(mapgen.explorer_map);
		pMap.printMap();
		map4msg = new PersonalMapLightWeight(pMap.getMap());

//		for(int i = 100; i<110; i++) {
//			mapgen = new MapGenerator(16, 16, 20, 0, 3, i);
//			mapgen.setHome(0, getMapYdim()/2, COLONY_1);
//			mapgen.setHome(getMapXdim()-1, getMapYdim()/2, COLONY_2);
//			mapgen.printMap();
//			System.out.println("Seed: " + i);
//		}

		mapgen.setHome(0, getMapYdim() / 2, COLONY_1);
		mapgen.setHome(getMapXdim() - 1, getMapYdim() / 2, COLONY_2);
		mapgen.printMap();
//		mapgen.printExplorerMap();

		addBehaviour(new CyclicBehaviour(this) {
			public void action() {
				ACLMessage msg = receive(request);
				if (msg != null)
					addBehaviour(new HandleRequest(myAgent, msg));
				block();
			}
		});

		// mapgen.map
		// mapgen.resource_map;
		// mapgen.explorer_map;

	}

	class HandleRequest extends OneShotBehaviour {
		ACLMessage msg, reply;
		String ConvID, content;
		int x, y;

		public HandleRequest(Agent a, ACLMessage msg) {
			super(a);
			this.msg = msg;
			content = msg.getContent();
		}

		public void action() {
			System.out.println(myAgent.getLocalName() + " <- content: " + content);
			String[] req = content.split("#");
			int req_key = Integer.parseInt(req[0]);

			switch (req_key) {
			case EXPLORE_LOCATION:
				x = Integer.parseInt(req[1]);
				y = Integer.parseInt(req[2]);
				System.out.println(" - " + myAgent.getLocalName() + " <- REQUEST from " + msg.getSender().getLocalName()
						+ ".  " + EXPLORE_LOCATION + ": explore square x:" + x + " y: " + y);
				reply = msg.createReply();
				reply.setPerformative(ACLMessage.INFORM);
				reply.setContent("" + getLocationInfo(x, y) + "#" + x + "#" + y);
				send(reply);
				break;

			case GIVE_MAP:
				System.out.println(" - " + myAgent.getLocalName() + " <- REQUEST from " + msg.getSender().getLocalName()
						+ ".  " + GIVE_MAP + ": give map for K" + req[1]);
				reply = msg.createReply();
				reply.setPerformative(ACLMessage.INFORM);
				try {
					reply.setContentObject(map4msg);
				} catch (Exception e) {
				}
				send(reply);
				break;

			case MINE_RESOURCES:
				x = Integer.parseInt(req[1]);
				y = Integer.parseInt(req[2]);
				System.out.println(" - " + myAgent.getLocalName() + " <- REQUEST from " + msg.getSender().getLocalName()
						+ ".  " + MINE_RESOURCES + ": mine resources at x:" + x + " y:" + y);

				reply = msg.createReply();
				reply.setPerformative(ACLMessage.INFORM);
				if (mapgen.resource_map[y][x] > 0) {
					System.out.println("b4 mine: " + mapgen.resource_map[y][x]);
					mapgen.resource_map[y][x] -= 1;
					System.out.println("after mine: " + mapgen.resource_map[y][x]);
					reply.setContent("" + SUCCESS);
				} else {
					setLocationInfo(x, y, '.');
					reply.setContent("" + FAILURE);
				}

				send(reply);
				break;

			default:
				break;
			}
		}

	} // --- Answer class ---

	char getLocationInfo(int x, int y) {
		return mapgen.map[y][x];
	}

	void setLocationInfo(int x, int y, char C) {
		mapgen.map[y][x] = C;
	}

	int getLocationValue(int x, int y) {
		return mapgen.resource_map[y][x];
	}

	int getMapYdim() {
		return mapgen.map.length;
	}

	int getMapXdim() {
		return mapgen.map[0].length;
	}

//  --- Methods to clean message que -------------------

	static long t0 = System.currentTimeMillis();

	class GCAgent extends TickerBehaviour {
		Set seen = new HashSet(), old = new HashSet();

		GCAgent(Agent a, long dt) {
			super(a, dt);
		}

		protected void onTick() {
			ACLMessage msg = myAgent.receive();
			while (msg != null) {
				if (!old.contains(msg))
					seen.add(msg);
				else {
					System.out.println("==== Flushing message:");
					dumpMessage(msg);
				}
				msg = myAgent.receive();
			}
			for (Iterator it = seen.iterator(); it.hasNext();)
				myAgent.putBack((ACLMessage) it.next());

			old.clear();
			Set tmp = old;
			old = seen;
			seen = tmp;
		}
	}

	void dumpMessage(ACLMessage msg) {
		System.out.print("t=" + (System.currentTimeMillis() - t0) / 1000F + " in " + getLocalName() + ": "
				+ ACLMessage.getPerformative(msg.getPerformative()));
		System.out
				.print("  from: " + (msg.getSender() == null ? "null" : msg.getSender().getLocalName()) + " --> to: ");
		for (Iterator it = msg.getAllReceiver(); it.hasNext();)
			System.out.print(((AID) it.next()).getLocalName() + ", ");
		System.out.println("  cid: " + msg.getConversationId());
		System.out.println("  content: " + msg.getContent());
	}
}
