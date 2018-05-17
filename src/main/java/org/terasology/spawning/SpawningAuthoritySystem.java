/*
 * Copyright 2017 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.spawning;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.entitySystem.entity.EntityManager;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.prefab.Prefab;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterMode;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.logic.inventory.InventoryComponent;
import org.terasology.logic.inventory.InventoryManager;
import org.terasology.logic.players.PlayerUtil;
import org.terasology.math.geom.Vector3f;
import org.terasology.minion.move.MinionMoveComponent;
import org.terasology.navgraph.WalkableBlock;
import org.terasology.network.NetworkComponent;
import org.terasology.registry.In;
import org.terasology.utilities.random.MersenneRandom;
import org.terasology.world.block.BlockManager;
import org.terasology.world.block.items.BlockItemComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RegisterSystem(RegisterMode.AUTHORITY)
public class SpawningAuthoritySystem extends BaseComponentSystem {
    private static final Logger logger = LoggerFactory.getLogger(SpawningAuthoritySystem.class);

    @In
    private BlockManager blockManager;

    @In
    private EntityManager entityManager;

    @In
    private InventoryManager inventoryManager;

    private Prefab prefabToSpawn;
    private Vector3f spawnPosition;


    /**
     * Spawns the desired Oreon at the location of Portal which sends the event
     */
    @ReceiveEvent
    public void oreonSpawn(OreonSpawnEvent event, EntityRef player) {
        prefabToSpawn = event.getOreonPrefab();
        spawnPosition = event.getSpawnPosition();

        boolean shouldSpawn = consumeItem(player, prefabToSpawn);
        if (shouldSpawn) {
            // spawn the new oreon into the world
            EntityRef newOreon = entityManager.create(prefabToSpawn, spawnPosition);
            NetworkComponent networkComponent = new NetworkComponent();
            networkComponent.replicateMode = NetworkComponent.ReplicateMode.ALWAYS;
            newOreon.addComponent(networkComponent);
            newOreon.getComponent(OreonSpawnComponent.class).parent = player;

            assignRandomAttributes(newOreon);

            logger.info("Player " + PlayerUtil.getColoredPlayerName(player) + " spawned a new Oreon of Type : " + prefabToSpawn.getName());
        }
    }

    private boolean consumeItem(EntityRef player, Prefab prefab) {
        OreonSpawnComponent oreonSpawnComponent = prefab.getComponent(OreonSpawnComponent.class);

        if (oreonSpawnComponent == null) {
            logger.info(prefab.getName() + " is not spawnable.");
            return false;
        }
        Map<String, Integer> itemsHashMap = oreonSpawnComponent.itemsToConsume;

        int numberOfItems = itemsHashMap.size();

        if (numberOfItems != 0) {
            if (player.hasComponent(InventoryComponent.class)) {
                List<Integer> requiredSlots = getSlotsForRequiredItems(itemsHashMap, player);

                //all required items not in inventory
                if (requiredSlots.size() != numberOfItems) {
                    logger.info("Could not find all required items in inventory");
                    return false;
                }

                for (int slotNumber = 0; slotNumber < numberOfItems; slotNumber++) {
                    if (!removeNeededItem(itemsHashMap, requiredSlots.get(slotNumber), player)) {
                        //could not decrement the required number from inventory
                        return false;
                    }
                }

                logger.info("Found all items required for spawning, creating your Oreon");
                return true;
            }

            logger.info("No inventory to source material from, cannot spawn");
            return false;
        }

        logger.info("No item required to spawn, spawning your Oreon");
        return true;
    }

    private List<Integer> getSlotsForRequiredItems(Map<String, Integer> items, EntityRef player) {
        List<Integer> requiredSlots = new ArrayList<>();

        int inventorySize = inventoryManager.getNumSlots(player);

        for (int slotNumber = 0; slotNumber <= inventorySize; slotNumber++) {
            EntityRef inventorySlot = inventoryManager.getItemInSlot(player, slotNumber);

            BlockItemComponent blockItemComponent = inventorySlot.getComponent(BlockItemComponent.class);
            if (blockItemComponent != null) {
                String blockFamilyName = blockItemComponent.blockFamily.toString();
                //if this item is required
                if (items.containsKey(blockFamilyName)) {
                    //check if required number is present in the inventory
                    int requiredNumber = items.get(blockFamilyName);
                    if (requiredNumber <= inventoryManager.getStackSize(inventorySlot)) {
                        requiredSlots.add(slotNumber);
                    } else {
                        logger.info("You don't have enough({} required) {} blocks to spawn the Oreon", requiredNumber, blockFamilyName);
                        break;
                    }
                }
            }
        }

        return requiredSlots;
    }

    private boolean removeNeededItem(Map<String, Integer> items, int slotNumber, EntityRef player) {
        EntityRef inventorySlot = inventoryManager.getItemInSlot(player, slotNumber);
        BlockItemComponent blockItemComponent = inventorySlot.getComponent(BlockItemComponent.class);
        String blockFamilyName = blockItemComponent.blockFamily.toString();

        logger.info("This Oreon has an item demand for spawning: " + blockFamilyName);
        logger.info("Found the item needed to spawn stuff! Decrementing by {}, then spawning", items.get(blockFamilyName));

        EntityRef result = inventoryManager.removeItem(player, player, inventorySlot, false, items.get(blockFamilyName));
        if (result == null) {
            logger.info("Could not decrement the required amount from inventory, not spawning");
            return false;
        }

        //successfully removed the required number of needed item
        return true;
    }

    private void assignRandomAttributes(EntityRef oreon) {
        OreonAttributeComponent oreonAttributes = new OreonAttributeComponent();

        MersenneRandom random = new MersenneRandom();

        if (prefabToSpawn.getName().equals(Constants.OREON_BUILDER_PREFAB)) {
            oreonAttributes.intelligence = random.nextInt(oreonAttributes.maxIntelligence);
        } else {
            oreonAttributes.intelligence = 0;
        }
        oreonAttributes.strength = random.nextInt(oreonAttributes.maxStrength);

        oreon.addComponent(oreonAttributes);
    }

}
