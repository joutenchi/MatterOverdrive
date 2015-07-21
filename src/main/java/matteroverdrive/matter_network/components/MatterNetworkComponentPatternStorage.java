/*
 * This file is part of Matter Overdrive
 * Copyright (c) 2015., Simeon Radivoev, All rights reserved.
 *
 * Matter Overdrive is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Matter Overdrive is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Matter Overdrive.  If not, see <http://www.gnu.org/licenses>.
 */

package matteroverdrive.matter_network.components;

import cofh.lib.util.TimeTracker;
import cpw.mods.fml.common.gameevent.TickEvent;
import matteroverdrive.Reference;
import matteroverdrive.api.network.IMatterNetworkClient;
import matteroverdrive.api.network.MatterNetworkTask;
import matteroverdrive.api.network.MatterNetworkTaskState;
import matteroverdrive.matter_network.MatterNetworkPacket;
import matteroverdrive.matter_network.packets.MatterNetworkRequestPacket;
import matteroverdrive.matter_network.packets.MatterNetworkTaskPacket;
import matteroverdrive.matter_network.packets.MatterNetworkResponsePacket;
import matteroverdrive.matter_network.tasks.MatterNetworkTaskStorePattern;
import matteroverdrive.tile.TileEntityMachinePatternStorage;
import matteroverdrive.util.MatterDatabaseHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * Created by Simeon on 7/15/2015.
 */
public class MatterNetworkComponentPatternStorage extends MatterNetworkComponentClient<TileEntityMachinePatternStorage>
{
    private TimeTracker taskProcessingTracker;

    public MatterNetworkComponentPatternStorage(TileEntityMachinePatternStorage patternStorage)
    {
        super(patternStorage);
        taskProcessingTracker = new TimeTracker();
    }

    @Override
    protected void manageResponsesQueuing(MatterNetworkResponsePacket packet)
    {

    }

    @Override
    protected void manageTaskPacketQueuing(MatterNetworkTaskPacket packet, MatterNetworkTask task)
    {
        if (taskProcessingTracker.hasDelayPassed(rootClient.getWorldObj(), rootClient.TASK_PROCESS_DELAY) && task.getState().belowOrEqual(MatterNetworkTaskState.QUEUED)) {
            if (task instanceof MatterNetworkTaskStorePattern) {
                if (rootClient.addItem(((MatterNetworkTaskStorePattern) task).getItemStack(), ((MatterNetworkTaskStorePattern) task).getProgress(), false, null)) {
                    //if the task is finished and the item is in the database
                    task.setState(MatterNetworkTaskState.FINISHED);
                } else {
                    //if the item could not be added to the database for some reason, and has passed the canProcess check
                    //then reset the task and set it to waiting
                    task.setState(MatterNetworkTaskState.WAITING);
                }
            }
        }
    }

    @Override
    protected void manageRequestsQueuing(MatterNetworkRequestPacket packet)
    {
        if (packet.getRequestType() == Reference.PACKET_REQUEST_PATTERN_SEARCH)
        {
            if (packet.getRequest() instanceof int[]) {
                int[] array = (int[]) packet.getRequest();
                NBTTagCompound tagCompound = rootClient.getItemAsNBT(new ItemStack(Item.getItemById(array[0]), 1, array[1]));
                if (tagCompound != null && packet.getSender(rootClient.getWorldObj()) instanceof IMatterNetworkClient) {
                    ((IMatterNetworkClient) packet.getSender(rootClient.getWorldObj())).queuePacket(new MatterNetworkResponsePacket(rootClient, Reference.PACKET_RESPONCE_VALID, packet.getRequestType(), tagCompound, packet.getSenderPort()), ForgeDirection.UNKNOWN);
                }
            }
        }else if (packet.getRequestType() == Reference.PACKET_REQUEST_VALID_PATTERN_DESTINATION)
        {
            if (packet.getRequest() instanceof NBTTagCompound)
            {
                NBTTagCompound pattern = (NBTTagCompound)packet.getRequest();
                NBTTagCompound tagCompound = rootClient.getItemAsNBT(ItemStack.loadItemStackFromNBT(pattern));
                if (tagCompound != null
                        && MatterDatabaseHelper.GetProgressFromNBT(tagCompound) >= MatterDatabaseHelper.MAX_ITEM_PROGRESS
                        && packet.getSender(rootClient.getWorldObj()) instanceof IMatterNetworkClient)
                {
                    ((IMatterNetworkClient) packet.getSender(rootClient.getWorldObj())).queuePacket(new MatterNetworkResponsePacket(rootClient, Reference.PACKET_RESPONCE_INVALID,packet.getRequestType(), tagCompound,packet.getSenderPort()), ForgeDirection.UNKNOWN);
                }
            }
        }
    }

    @Override
    public boolean canPreform(MatterNetworkPacket packet) {
        if (super.canPreform(packet) && rootClient.getRedstoneActive())
        {
            if (packet instanceof MatterNetworkTaskPacket) {
                if (((MatterNetworkTaskPacket) packet).getTask(rootClient.getWorldObj()) instanceof MatterNetworkTaskStorePattern) {
                    MatterNetworkTaskStorePattern task = (MatterNetworkTaskStorePattern) ((MatterNetworkTaskPacket) packet).getTask(rootClient.getWorldObj());
                    return rootClient.addItem(task.getItemStack(), task.getProgress(), true, null);
                }
            } else if (packet instanceof MatterNetworkRequestPacket) {
                MatterNetworkRequestPacket requestPacket = (MatterNetworkRequestPacket) packet;
                return requestPacket.getRequestType() == Reference.PACKET_REQUEST_CONNECTION
                        || requestPacket.getRequestType() == Reference.PACKET_REQUEST_PATTERN_SEARCH
                        || requestPacket.getRequestType() == Reference.PACKET_REQUEST_NEIGHBOR_CONNECTION
                        || requestPacket.getRequestType() == Reference.PACKET_REQUEST_VALID_PATTERN_DESTINATION;
            }
        }
        return false;
    }

    @Override
    public void queuePacket(MatterNetworkPacket packet, ForgeDirection from)
    {
        manageBasicPacketsQueuing(rootClient,rootClient.getWorldObj(),packet,from);
    }

    @Override
    public int onNetworkTick(World world, TickEvent.Phase phase) {
        return 0;
    }
}