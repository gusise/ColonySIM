package colony.environment;

public class PersonalMapLightWeight implements java.io.Serializable {

	private char[][] map;

	public PersonalMapLightWeight(char[][] map) {
		this.map = new char[map.length][map[0].length];
		this.map = map;
	}

	public char[][] getMap() {
		return map;
	}

	public char[][] setMap() {
		return map;
	}
}
