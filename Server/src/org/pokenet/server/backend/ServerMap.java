package org.pokenet.server.backend;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

import org.pokenet.server.GameServer;
import org.pokenet.server.backend.entity.Char;
import org.pokenet.server.backend.entity.NonPlayerChar;
import org.pokenet.server.backend.entity.PlayerChar;
import org.pokenet.server.backend.entity.Positionable.Direction;
import org.pokenet.server.battle.Pokemon;

import tiled.core.Map;
import tiled.core.TileLayer;

/**
 * Represents a map in the game world
 * @author shadowkanji
 *
 */
public class ServerMap {
	public enum PvPType { DISABLE, ENABLED, ENFORCED }
	
	//Stores the width, heigth, x, y and offsets of this map
	private int m_width;
	private int m_heigth;
	private int m_x;
	private int m_y;
	private int m_xOffsetModifier;
	private int m_yOffsetModifier;
	private PvPType m_pvpType = PvPType.ENABLED;
	private ServerMapMatrix m_mapMatrix;
	//Players and NPCs
	private ArrayList<PlayerChar> m_players;
	private ArrayList<NonPlayerChar> m_npcs;
	//The following stores information for day, night and water wild pokemon
	private HashMap<String, int[]> m_dayPokemonLevels;
	private HashMap<String, Integer> m_dayPokemonChances;
	private HashMap<String, int[]> m_nightPokemonLevels;
	private HashMap<String, Integer> m_nightPokemonChances;
	private HashMap<String, int[]> m_waterPokemonLevels;
	private HashMap<String, Integer> m_waterPokemonChances;
	private int m_wildProbability;
	//The following stores collision information
	private TileLayer m_blocked = null;
	private TileLayer m_surf = null;
	private TileLayer m_grass = null;
	private TileLayer m_ledgesDown = null;
	private TileLayer m_ledgesLeft = null;
	private TileLayer m_ledgesRight = null;
	//Misc
	private Random m_random = GameServer.getServiceManager().getDataService().getBattleMechanics().getRandom();
	
	/**
	 * Default constructor
	 * @param map
	 * @param x
	 * @param y
	 */
	public ServerMap(Map map, int x, int y) {
		m_x = x;
		m_y = y;
		m_heigth = map.getHeight();
		m_width = map.getWidth();
		/*
		 * Store all the map layers
		 */
		for(int i = 0; i < map.getTotalLayers(); i++) {
			if(map.getLayer(i).getName().equalsIgnoreCase("Grass")) {
				m_grass = (TileLayer) map.getLayer(i);
			} else if(map.getLayer(i).getName().equalsIgnoreCase("Collisions")) {
				m_blocked = (TileLayer) map.getLayer(i);
			} else if(map.getLayer(i).getName().equalsIgnoreCase("LedgesLeft")) {
				m_ledgesLeft = (TileLayer) map.getLayer(i);
			} else if(map.getLayer(i).getName().equalsIgnoreCase("LedgesRight")) {
				m_ledgesRight = (TileLayer) map.getLayer(i);
			} else if(map.getLayer(i).getName().equalsIgnoreCase("LedgesDown")) {
				m_ledgesDown = (TileLayer) map.getLayer(i);
			} else if(map.getLayer(i).getName().equalsIgnoreCase("Water")) {
				m_surf = (TileLayer) map.getLayer(i);
			}
		}
	}
	
	/**
	 * Adds a player to this map and notifies all other clients on the map.
	 * @param player
	 */
	public void addChar(Char c) {
		if(c instanceof PlayerChar) {
			m_players.add((PlayerChar) c);
		} else if(c instanceof NonPlayerChar) {
			//Set the id of the npc
			c.setId(-1 - m_npcs.size());
			m_npcs.add((NonPlayerChar) c);
		}
		for(int i = 0; i < m_players.size(); i++) {
			if(c.getId() != m_players.get(i).getId())
				m_players.get(i).getSession().write("ma" + c.getName() + "," + 
					c.getId() + "," + c.getSprite() + "," + c.getX() + "," + c.getY() + "," + c.getFacing());
		}
	}
	
	/**
	 * Adds a char and sets their x y based on a 32 by 32 pixel grid.
	 * Allows easier adding of NPCs as the x,y can easily be counted via Tiled
	 * @param c
	 * @param tileX
	 * @param tileY
	 */
	public void addChar(Char c, int tileX, int tileY) {
		this.addChar(c);
		c.setX(tileX * 32);
		c.setY((tileY * 32) - 8);
	}
	
	/**
	 * Returns the x co-ordinate of this servermap in the map matrix
	 * @return
	 */
	public int getX() {
		return m_x;
	}
	
	/**
	 * Returns the y co-ordinate of this servermap in the map matrix
	 * @return
	 */
	public int getY() {
		return m_y;
	}
	
	/**
	 * Returns the width of this map
	 * @return
	 */
	public int getWidth() {
		return m_width;
	}
	
	/**
	 * Returns the height of this map
	 * @return
	 */
	public int getHeight() {
		return m_heigth;
	}
	
	/**
	 * Returns the x offset of this map
	 * @return
	 */
	public int getXOffsetModifier() {
		return m_xOffsetModifier;
	}
	
	/**
	 * Returns the y offset of this map
	 * @return
	 */
	public int getYOffsetModifier() {
		return m_yOffsetModifier;
	}
	
	/**
	 * Removes a char from this map
	 * @param c
	 */
	public void removeChar(Char c) {
		if(c instanceof PlayerChar) {
			m_players.remove((PlayerChar) c);
			m_players.trimToSize();
		} else if(c instanceof NonPlayerChar) {
			m_npcs.remove((NonPlayerChar) c);
			m_npcs.trimToSize();
		}
		for(int i = 0; i < m_players.size(); i++) {
			m_players.get(i).getSession().write("mr" + c.getId());
		}
	}
	
	/**
	 * Returns true if there is an obstacle
	 * @param x
	 * @param y
	 * @param d
	 * @return
	 */
	private boolean isBlocked(int x, int y, Direction d) {
		if (m_blocked.getTileAt(x, y) != null)
			return true;
		//TODO: Npc check
		if(m_ledgesRight != null && m_ledgesRight.getTileAt(x, y) != null) {
			if(d == Direction.Left || d == Direction.Up || d == Direction.Down)
				return true;
		}
		if(m_ledgesLeft != null && m_ledgesLeft.getTileAt(x, y) != null) {
			if(d == Direction.Right || d == Direction.Up || d == Direction.Down)
				return true;
		}
		if(m_ledgesDown != null && m_ledgesDown.getTileAt(x, y) != null) {
			if(d == Direction.Left || d == Direction.Up || d == Direction.Right)
				return true;
		}
		return false;
	}
	
	/**
	 * Attempts to move a char and sends the movement to everyone, returns true on success
	 * @param c
	 * @param d
	 */
	public boolean moveChar(Char c, Direction d) {
		int playerX = c.getX();
		int playerY = c.getY();
		int newX;
		int newY;

		switch(d) {
		case Up:
			newX = playerX / 32;
			newY = ((playerY + 8) - 32) / 32;
			if (playerY >= 1) {
				if (!isBlocked(newX, newY, Direction.Up)) {
					if(m_surf != null && m_surf.getTileAt(newX, newY) != null) {
						if(c.isSurfing()) {
							return true;
						} else {
							if(c instanceof PlayerChar) {
								PlayerChar p = (PlayerChar) c;
								if(p.canSurf()) {
									p.setSurfing(true);
									return true;
								} else {
									return false;
								}
							}
						}
					} else {
						if(c.isSurfing())
							c.setSurfing(false);
						//TODO: Add warp check
						return true;
					}
				}
			} else {
				ServerMap newMap = m_mapMatrix.getMapByGamePosition(m_x, m_y - 1);
				if (newMap != null) {
					m_mapMatrix.moveBetweenMaps(c, this, newMap);
				}
			}
			break;
		case Down:
			newX = playerX / 32;
			newY = ((playerY + 8) + 32) / 32;
			if (playerY + 40 < m_heigth * 32) {
				if (!isBlocked(newX, newY, Direction.Down)) {
					if(m_surf != null && m_surf.getTileAt(newX, newY) != null) {
						if(c.isSurfing()) {
							return true;
						} else {
							if(c instanceof PlayerChar) {
								PlayerChar p = (PlayerChar) c;
								if(p.canSurf()) {
									p.setSurfing(true);
									return true;
								} else {
									return false;
								}
							}
						}
					} else {
						if(c.isSurfing())
							c.setSurfing(false);
						//TODO: Warp check
						return true;
					}
				}
			} else {
				ServerMap newMap = m_mapMatrix.getMapByGamePosition(m_x, m_y + 1);
				if (newMap != null) {
					m_mapMatrix.moveBetweenMaps(c, this, newMap);
				}
			}
			break;
		case Left:
			newX = (playerX - 32) / 32;
			newY = (playerY + 8) / 32;
			if (playerX >= 32) {
				if (!isBlocked(newX, newY, Direction.Left)) {
					if(m_surf != null && m_surf.getTileAt(newX, newY) != null) {
						if(c.isSurfing()) {
							return true;
						} else {
							if(c instanceof PlayerChar) {
								PlayerChar p = (PlayerChar) c;
								if(p.canSurf()) {
									p.setSurfing(true);
									return true;
								} else {
									return false;
								}
							}
						}
					} else {
						if(c.isSurfing())
							c.setSurfing(false);
						//TODO: Warp check
						return true;
					}
				}
			} else {
				ServerMap newMap = m_mapMatrix.getMapByGamePosition(m_x - 1, m_y);
				if (newMap != null) {
					m_mapMatrix.moveBetweenMaps(c, this, newMap);
				}
			}
			break;
		case Right:
			newX = (playerX + 32) / 32;
			newY = (playerY + 8) / 32;
			if (playerX + 32 < m_width * 32) {
				if (!isBlocked(newX, newY, Direction.Right)) {
					if(m_surf != null && m_surf.getTileAt(newX, newY) != null) {
						if(c.isSurfing()) {
							return true;
						} else {
							if(c instanceof PlayerChar) {
								PlayerChar p = (PlayerChar) c;
								if(p.canSurf()) {
									p.setSurfing(true);
									return true;
								} else {
									return false;
								}
							}
						}
					} else {
						if(c.isSurfing())
							c.setSurfing(false);
						//TODO: Warp check
						return true;
					}
				}
			} else {
				ServerMap newMap = m_mapMatrix.getMapByGamePosition(m_x + 1, m_y);
				if (newMap != null) {
					m_mapMatrix.moveBetweenMaps(c, this, newMap);
				}
			}
			break;
		}
		return false;
	}
	
	/**
	 * Returns true if a wild pokemon was encountered.
	 * @return
	 */
	public boolean isWildBattle(int x, int y) {
		if (m_random.nextInt(2874) < m_wildProbability * 16)
			if (m_grass != null && m_grass.getTileAt(x / 32, y / 32) != null)
				return true;
		return false;
	}
	
	/**
	 * Returns a wild pokemon.
	 * Different players have different chances of encountering rarer Pokemon.
	 * @return
	 */
	public Pokemon getWildPokemon(PlayerChar p) {
		return null;
	}
	
	/**
	 * Sends a packet to all players on the map
	 * @param message
	 */
	public void sendToAll(String message) {
		for(int i = 0; i < m_players.size(); i++) {
			m_players.get(i).getSession().write(message);
		}
	}
	
	/**
	 * Returns the arraylist of players
	 * @return
	 */
	public ArrayList<PlayerChar> getPlayers() {
		return m_players;
	}
	
	/**
	 * Returns the arraylist of npcs
	 * @return
	 */
	public ArrayList<NonPlayerChar> getNpcs() {
		return m_npcs;
	}
}
