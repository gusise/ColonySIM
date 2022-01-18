package colony.environment;

import java.util.Arrays;
import java.util.Random;

import colony.ColonyVocabulary;

public class MapGenerator implements ColonyVocabulary {
	// ? - unexplored
	// . - nothing there
	// $ - resources
	// 1 to 9 - bases

	public char[][] map;
	public char[][] explorer_map;
	public int[][] resource_map;

	private Random rng;

	public MapGenerator(int xdim, int ydim, int n_rp, int x0, int y0, long seed) {
		try {
			rng = new Random(seed);

			map = new char[ydim][xdim];
			resource_map = new int[ydim][xdim];
			explorer_map = new char[ydim][xdim];

			for (char[] x : map)
				Arrays.fill(x, EMPTY);
			for (char[] x : explorer_map)
				Arrays.fill(x, UNEXPLORED);
			for (int[] x : resource_map)
				Arrays.fill(x, 0);
			populateMap(n_rp);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void setHome(int xbase, int ybase, char baseNR) {
		try {
			map[ybase][xbase] = baseNR;
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	private void populateMap(int n_rp) {
		for (int i = 0; i < n_rp; i++) {
			int x = rng.nextInt(map[0].length - 2) + 1;
			int y = rng.nextInt(map.length - 2) + 1;
			map[y][x] = RESOURCES;
			resource_map[y][x] = 9;
		}
	}

	public void printMap() {

		System.out.println("\nMap:");
		for (int r = 0; r < map.length; r++) { // for loop for row iteration.
			for (int c = 0; c < map[r].length; c++) { // for loop for column iteration.
				System.out.print(map[r][c] + " ");
			}
			System.out.println(); // using this for new line to print array in matrix format.
		}
	}

	public void printResourceMap() {

		System.out.println("\nResources:");
		for (int r = 0; r < resource_map.length; r++) { // for loop for row iteration.
			for (int c = 0; c < resource_map[r].length; c++) { // for loop for column iteration.
				System.out.print(resource_map[r][c] + " ");
			}
			System.out.println(); // using this for new line to print array in matrix format.
		}
	}

	public void printExplorerMap() {

		System.out.println("\nExplorers map:");
		for (int r = 0; r < explorer_map.length; r++) { // for loop for row iteration.
			for (int c = 0; c < explorer_map[r].length; c++) { // for loop for column iteration.
				System.out.print(explorer_map[r][c] + " ");
			}
			System.out.println(); // using this for new line to print array in matrix format.
		}
	}

}
