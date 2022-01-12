package colony.environment;

public class PersonalMap implements java.io.Serializable {

	private char[][] map;

	public PersonalMap(char[][] map) {
		this.map = new char[map.length][map[0].length];
		this.map = map;
	}

	public char[][] getMap() {
		return map;
	}

	public char getLocation(int x, int y) {
		return map[y][x];
	}

	public void setMap(char[][] new_map) {
		this.map = new_map;
	}

	public void mergeMap(char[][] other_map) {
		for (int r = 0; r < this.map.length; r++) {
			for (int c = 0; c < this.map[0].length; c++) {
				if (this.map[r][c] == '?' & other_map[r][c] != '?')
					this.map[r][c] = other_map[r][c];
				if (this.map[r][c] == '$' & other_map[r][c] == 'X')
					this.map[r][c] = other_map[r][c];
			}
		}
	}

	public void updateMap(int x, int y, char newMark) {
		this.map[y][x] = newMark;
	}

	public void printMap() {

		System.out.println("\nPersonal map:");
		for (int r = 0; r < map.length; r++) { // for loop for row iteration.
			for (int c = 0; c < map[r].length; c++) { // for loop for column iteration.
				System.out.print(map[r][c] + " ");
			}
			System.out.println(); // using this for new line to print array in matrix format.
		}
	}

	public int getMapYdim() {
		return map.length;
	}

	public int getMapXdim() {
		return map[0].length;
	}

	public boolean hasUnexploredLocation() {
		for (int r = 0; r < this.map.length; r++) {
			for (int c = 0; c < this.map[0].length; c++) {
				if (this.map[r][c] == '?')
					return true;
			}
		}
		return false;
	}

	public boolean hasResourceLocation() {
		for (int r = 0; r < this.map.length; r++) {
			for (int c = 0; c < this.map[0].length; c++) {
				if (this.map[r][c] == '$')
					return true;
			}
		}
		return false;
	}

	public String getOpenMine() {
		for (int r = 0; r < this.map.length; r++) {
			for (int c = 0; c < this.map[0].length; c++) {
				if (this.map[r][c] == '$')
					return c + "#" + r;
			}
		}
		return null;
	}

	public int countInstance(char C) {
		int cntr = 0;
		for (int r = 0; r < this.map.length; r++) {
			for (int c = 0; c < this.map[0].length; c++) {
				if (this.map[r][c] == C)
					cntr++;
			}
		}
		return cntr;
	}

}
