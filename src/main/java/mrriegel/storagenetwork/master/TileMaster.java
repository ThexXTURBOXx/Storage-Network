package mrriegel.storagenetwork.master;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.apache.logging.log4j.Level;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import mrriegel.storagenetwork.ConfigHandler;
import mrriegel.storagenetwork.IConnectable;
import mrriegel.storagenetwork.StorageNetwork;
import mrriegel.storagenetwork.cable.TileCable;
import mrriegel.storagenetwork.cable.TileCable.Kind;
import mrriegel.storagenetwork.helper.FilterItem;
import mrriegel.storagenetwork.helper.InvHelper;
import mrriegel.storagenetwork.helper.NBTHelper;
import mrriegel.storagenetwork.helper.StackWrapper;
import mrriegel.storagenetwork.helper.Util;
import mrriegel.storagenetwork.items.ItemUpgrade;
import mrriegel.storagenetwork.tile.AbstractFilterTile;
import mrriegel.storagenetwork.tile.AbstractFilterTile.Direction;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemHoe;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.oredict.OreDictionary;

public class TileMaster extends TileEntity implements ITickable {
  public Set<BlockPos> connectables;
  public List<BlockPos> storageInventorys;
  public List<StackWrapper> getStacks() {
    List<StackWrapper> stacks = Lists.newArrayList();
    List<AbstractFilterTile> invs = Lists.newArrayList();
    if (connectables == null) {
      refreshNetwork();
    }
    for (BlockPos p : connectables) {
      if (world.getTileEntity(p) instanceof AbstractFilterTile) {
        AbstractFilterTile tile = (AbstractFilterTile) world.getTileEntity(p);
        if (tile.isStorage() && tile.getInventory() != null) {
          invs.add(tile);
        }
      }
    }
    for (AbstractFilterTile t : invs) {
      IItemHandler inv = t.getInventory();
      //StorageNetwork.log(" inventory size  " + inv.getSlots());
      for (int i = 0; i < inv.getSlots(); i++) {
        if (inv.getStackInSlot(i) != null && !inv.getStackInSlot(i).isEmpty() && t.canTransfer(inv.getStackInSlot(i), Direction.BOTH))
          addToList(stacks, inv.getStackInSlot(i).copy(), inv.getStackInSlot(i).getCount());
        //        else
        //          StorageNetwork.log(" reject   " + inv.getStackInSlot(i).getDisplayName());
      }
    }
    return stacks;
  }
  public int emptySlots() {
    int res = 0;
    //    List<StackWrapper> stacks = Lists.newArrayList();
    List<AbstractFilterTile> invs = Lists.newArrayList();
    for (BlockPos p : connectables) {
      if (world.getTileEntity(p) instanceof AbstractFilterTile) {
        AbstractFilterTile tile = (AbstractFilterTile) world.getTileEntity(p);
        if (tile.isStorage() && tile.getInventory() != null) {
          invs.add(tile);
        }
      }
    }
    for (AbstractFilterTile t : invs) {
      IItemHandler inv = t.getInventory();
      for (int i = 0; i < inv.getSlots(); i++) {
        if (inv.getStackInSlot(i) == null || inv.getStackInSlot(i).isEmpty()) {
          res++;
        }
      }
    }
    return res;
  }
  public List<StackWrapper> getCraftableStacks(List<StackWrapper> stacks) {
    List<StackWrapper> craftableStacks = Lists.newArrayList();
    //    List<AbstractTileContainer> invs = Lists.newArrayList();
    //    for (BlockPos p : connectables) {
    //      if (!(world.getTileEntity(p) instanceof AbstractTileContainer))
    //        continue;
    ////      AbstractTileContainer tile = (AbstractTileContainer) world.getTileEntity(p);
    ////      invs.add(tile);
    //    }
    //    for (AbstractTileContainer t : invs) {
    //      for (int i = 0; i < t.getSizeInventory(); i++) {
    //        if (t.getStackInSlot(i) != null && !t.getStackInSlot(i).isEmpty()) {
    //          NBTTagCompound res = (NBTTagCompound) t.getStackInSlot(i).getTagCompound().getTag("res");
    //          if (!Util.contains(stacks, new StackWrapper(new ItemStack(res), 0), new Comparator<StackWrapper>() {
    //            @Override
    //            public int compare(StackWrapper o1, StackWrapper o2) {
    //              if (ItemHandlerHelper.canItemStacksStack(o1.getStack(), o2.getStack())) { return 0; }
    //              return 1;
    //            }
    //          })) {
    //            addToList(craftableStacks, new ItemStack(res), 0);
    //          }
    //        }
    //      }
    //    }
    return craftableStacks;
  }
  private void addToList(List<StackWrapper> lis, ItemStack s, int num) {
    boolean added = false;
    for (int i = 0; i < lis.size(); i++) {
      ItemStack stack = lis.get(i).getStack();
      if (ItemHandlerHelper.canItemStacksStack(s, stack)) {
        lis.get(i).setSize(lis.get(i).getSize() + num);
        added = true;
      }
      else {
        //        lis.add(new StackWrapper(stack,stack.getCount()));
      }
    }
    if (!added) {
      lis.add(new StackWrapper(s, num));
    }
  }
  public int getAmount(FilterItem fil) {
    if (fil == null) { return 0; }
    int size = 0;
    //ItemStack s = fil.getStack();
    for (StackWrapper w : getStacks()) {
      if (fil.match(w.getStack()))
        size += w.getSize();
    }
    return size;
  }
  public List<FilterItem> getIngredients(ItemStack template) {
    Map<Integer, ItemStack> stacks = Maps.<Integer, ItemStack> newHashMap();
    Map<Integer, Boolean> metas = Maps.<Integer, Boolean> newHashMap();
    Map<Integer, Boolean> ores = Maps.<Integer, Boolean> newHashMap();
    NBTTagList invList = template.getTagCompound().getTagList("crunchItem", Constants.NBT.TAG_COMPOUND);
    for (int i = 0; i < invList.tagCount(); i++) {
      NBTTagCompound stackTag = invList.getCompoundTagAt(i);
      int slot = stackTag.getByte("Slot");
      stacks.put(slot, new ItemStack(stackTag));
    }
    List<FilterItem> list = Lists.newArrayList();
    for (int i = 1; i < 10; i++) {
      metas.put(i - 1, NBTHelper.getBoolean(template, "meta" + i));
      ores.put(i - 1, NBTHelper.getBoolean(template, "ore" + i));
    }
    for (Entry<Integer, ItemStack> e : stacks.entrySet()) {
      if (e.getValue() != null) {
        boolean meta = metas.get(e.getKey()), ore = ores.get(e.getKey());
        list.add(new FilterItem(e.getValue(), meta, ore, false));
      }
    }
    return list;
  }
  @Override
  public NBTTagCompound getUpdateTag() {
    return writeToNBT(new NBTTagCompound());
  }
  private void addConnectables(final BlockPos pos) {
    if (pos == null || world == null || this.getWorld().isBlockLoaded(pos) == false) { return; }
    for (BlockPos bl : Util.getSides(pos)) {
      if (this.getWorld().isBlockLoaded(bl) == false) {
        continue;
      }
      Chunk chunk = world.getChunkFromBlockCoords(bl);
      if (chunk == null || !chunk.isLoaded()) {
        continue;
      }
      if (world.getTileEntity(bl) != null && world.getTileEntity(bl) instanceof TileMaster && !bl.equals(this.pos)) {
        world.getBlockState(bl).getBlock().dropBlockAsItem(world, bl, world.getBlockState(bl), 0);
        world.setBlockToAir(bl);
        world.removeTileEntity(bl);
        continue;
      }
      if (world.getTileEntity(bl) != null && world.getTileEntity(bl) instanceof IConnectable && !connectables.contains(bl)) {
        connectables.add(bl);
        ((IConnectable) world.getTileEntity(bl)).setMaster(this.pos);
        chunk.setModified(true);
        addConnectables(bl);
      }
    }
  }
  private void addInventorys() {
    storageInventorys = Lists.newArrayList();
    for (BlockPos cable : connectables) {
      if (world.getTileEntity(cable) instanceof AbstractFilterTile) {
        AbstractFilterTile s = (AbstractFilterTile) world.getTileEntity(cable);
        if (s.getInventory() != null && s.isStorage()) {
          BlockPos pos = s.getSource();
          if (world.getChunkFromBlockCoords(pos).isLoaded())
            storageInventorys.add(pos);
        }
      }
    }
  }
  public void refreshNetwork() {
    if (world.isRemote) { return; }
    connectables = Sets.newHashSet();
    try {
      addConnectables(pos);
    }
    catch (Error e) {
      e.printStackTrace();
    }
    addInventorys();
    world.getChunkFromBlockCoords(pos).setModified(true);//.setChunkModified();
  }
  public int insertStack(ItemStack stack, BlockPos source, boolean simulate) {
    if (stack == null || stack.isEmpty()) { return 0; }
    List<AbstractFilterTile> invs = Lists.newArrayList();
    if (connectables == null) {
      refreshNetwork();
    }
    for (BlockPos p : connectables) {
      if (world.getTileEntity(p) instanceof AbstractFilterTile) {
        AbstractFilterTile tile = (AbstractFilterTile) world.getTileEntity(p);
        if (tile.isStorage() && tile.getInventory() != null) {
          invs.add(tile);
        }
      }
    }
    Collections.sort(invs, new Comparator<AbstractFilterTile>() {
      @Override
      public int compare(AbstractFilterTile o1, AbstractFilterTile o2) {
        return Integer.compare(o2.getPriority(), o1.getPriority());
      }
    });
    ItemStack in = stack.copy();
    for (AbstractFilterTile t : invs) {
      IItemHandler inv = t.getInventory();
      if (!InvHelper.contains(inv, in))
        continue;
      if (!t.canTransfer(in, Direction.IN))
        continue;
      if (t.getSource().equals(source))
        continue;
      ItemStack remain = ItemHandlerHelper.insertItemStacked(inv, in, simulate);
      if (remain == null || remain.isEmpty())
        return 0;
      in = ItemHandlerHelper.copyStackWithSize(in, remain.getCount());
      world.markChunkDirty(t.getSource(), world.getTileEntity(t.getSource()));
    }
    for (AbstractFilterTile t : invs) {
      IItemHandler inv = t.getInventory();
      if (InvHelper.contains(inv, in))
        continue;
      if (!t.canTransfer(in, Direction.IN))
        continue;
      if (t.getSource().equals(source))
        continue;
      ItemStack remain = ItemHandlerHelper.insertItem(inv, in, simulate);
      if (remain == null || remain.isEmpty())
        return 0;
      in = ItemHandlerHelper.copyStackWithSize(in, remain.getCount());
      world.markChunkDirty(t.getSource(), world.getTileEntity(t.getSource()));
    }
    return in.getCount();
  }
  public void updateImports() {
    List<TileCable> invs = Lists.newArrayList();
    for (BlockPos p : connectables) {
      if (!(world.getTileEntity(p) instanceof TileCable))
        continue;
      TileCable tile = (TileCable) world.getTileEntity(p);
      if (tile.getKind() == Kind.imKabel && tile.getInventory() != null) {
        invs.add(tile);
      }
    }
    Collections.sort(invs, new Comparator<TileCable>() {
      @Override
      public int compare(TileCable o1, TileCable o2) {
        return Integer.compare(o2.getPriority(), o1.getPriority());
      }
    });
    for (TileCable t : invs) {
      IItemHandler inv = t.getInventory();
      if ((world.getTotalWorldTime() + 10) % (30 / (t.getUpgradesOfType(ItemUpgrade.SPEED) + 1)) != 0)
        continue;
      for (int i = 0; i < inv.getSlots(); i++) {
        ItemStack s = inv.getStackInSlot(i);
        if (s == null || s.isEmpty()) {
          continue;
        }
        if (!t.canTransfer(s, Direction.OUT)) {
          continue;
        }
        if (!t.status()) {
          continue;
        }
        // int num = s.getCount();
        int insert = Math.min(s.getCount(), (int) Math.pow(2, t.getUpgradesOfType(ItemUpgrade.STACK) + 2));
        ItemStack extracted = inv.extractItem(i, insert, true);
        if (extracted == null || extracted.getCount() < insert) {
          continue;
        }
        int rest = insertStack(ItemHandlerHelper.copyStackWithSize(s, insert), t.getConnectedInventory(), false);
        inv.extractItem(i, insert - rest, false);
        world.markChunkDirty(pos, this);
        break;
      }
    }
  }
  public void updateExports() {
    List<TileCable> invs = Lists.newArrayList();
    for (BlockPos p : connectables) {
      if (!(world.getTileEntity(p) instanceof TileCable)) {
        continue;
      }
      TileCable tile = (TileCable) world.getTileEntity(p);
      if (tile.getKind() == Kind.exKabel && tile.getInventory() != null) {
        invs.add(tile);
      }
    }
    Collections.sort(invs, new Comparator<TileCable>() {
      @Override
      public int compare(TileCable o1, TileCable o2) {
        return Integer.compare(o1.getPriority(), o2.getPriority());
      }
    });
    for (TileCable t : invs) {
      if (t == null) {
        continue;
      }
      IItemHandler inv = t.getInventory();
      if (inv == null) {
        continue;
      }
      if ((world.getTotalWorldTime() + 20) % (30 / (t.getUpgradesOfType(ItemUpgrade.SPEED) + 1)) != 0) {
        continue;
      }
      for (int i = 0; i < 18; i++) {
        if (t.getFilter().get(i) == null) {
          continue;
        }
        boolean ore = t.getOre(i);
        boolean meta = t.getMeta(i);
        ItemStack stackToFilter = t.getFilter().get(i).getStack().copy();
        //        ItemStack stackToFilter= t.getFilter().get(i).getStack();
        if (stackToFilter.getItem() instanceof ItemArmor
            || stackToFilter.getItem() instanceof ItemTool
            || stackToFilter.getItem() instanceof ItemSword
            || stackToFilter.getItem() instanceof ItemBow
            || stackToFilter.getItem() instanceof ItemHoe) {
          //          stackToFilter.setItemDamage( OreDictionary.WILDCARD_VALUE);
          //TODO: SUPER HACK. set this in GUI i guess? 
          meta = false;
        }
        if (stackToFilter == null || stackToFilter.isEmpty()) {
          continue;
        }
        if (storageInventorys.contains(t.getPos())) {
          continue;
        }
        FilterItem filter = new FilterItem(stackToFilter, meta, ore, false);
        ItemStack stackCurrent = request(filter, 1, true);
        if (stackCurrent == null || stackCurrent.isEmpty()) {
          continue;
        }
        int maxStackSize = stackCurrent.getMaxStackSize();
        if ((t.getUpgradesOfType(ItemUpgrade.STOCK) > 0)) {
          maxStackSize = Math.min(maxStackSize, t.getFilter().get(i).getSize() - InvHelper.getAmount(inv, new FilterItem(stackCurrent, meta, ore, false)));
        }
        if (maxStackSize <= 0) {
          continue;
        }
        ItemStack max = ItemHandlerHelper.copyStackWithSize(stackCurrent, maxStackSize);
        ItemStack remain = ItemHandlerHelper.insertItemStacked(inv, max, true);
        int insert = remain == null ? max.getCount() : max.getCount() - remain.getCount();
        insert = Math.min(insert, (int) Math.pow(2, t.getUpgradesOfType(ItemUpgrade.STACK) + 2));
        if (!t.status()) {
          continue;
        }
        ItemStack rec = request(new FilterItem(stackCurrent, meta, ore, false), insert, false);
        if (rec == null || rec.isEmpty()) {
          continue;
        }
        rec.shrink(t.getUpgradesOfType(ItemUpgrade.SPEED));
        ItemHandlerHelper.insertItemStacked(inv, rec, false);
        world.markChunkDirty(pos, this);
        break;
      }
    }
  }
  public ItemStack request(FilterItem fil, final int size, boolean simulate) {
    if (size == 0 || fil == null) { return ItemStack.EMPTY; }
    List<AbstractFilterTile> invs = Lists.newArrayList();
    for (BlockPos p : connectables) {
      if (world.getTileEntity(p) instanceof AbstractFilterTile) {
        AbstractFilterTile tile = (AbstractFilterTile) world.getTileEntity(p);
        if (tile.isStorage() && tile.getInventory() != null) {
          invs.add(tile);
        }
      }
    }
    //only match meta if NOT wildca rd
    //    if(fil.getStack().getMetadata() == OreDictionary.WILDCARD_VALUE ){
    //     
    //      //fil.setMeta(false);
    //      StorageNetwork.log("!TileMaster ATTEMPT to request IGNORE meta ___ "+fil.getStack());
    //    }
    // 
    //fil.setOre(fil.getStack().getMetadata() == OreDictionary.WILDCARD_VALUE);
    //    fil.setOre(true);//TODO: where why this set?. anyway just ALWAYS use ore dictionary. because fuck yeah 
    ItemStack res = ItemStack.EMPTY;
    int result = 0;
    for (AbstractFilterTile t : invs) {
      IItemHandler inv = t.getInventory();
      for (int i = 0; i < inv.getSlots(); i++) {
        ItemStack stackCurrent = inv.getStackInSlot(i);
        if (stackCurrent == null || stackCurrent.isEmpty()) {
          continue;
        }
        if (res != null && !res.isEmpty() && !ItemHandlerHelper.canItemStacksStack(stackCurrent, res)) {
          continue;
        }
        if (!fil.match(stackCurrent)) {
          continue;
        }
        if (!t.canTransfer(stackCurrent, Direction.OUT)) {
          continue;
        }
        //        StorageNetwork.log("so res is NOT? air?" + res + "?" + res.isEmpty() + res.getDisplayName());
        //        StorageNetwork.log("sss" + stackCurreint + "?" + stackCurrent.isEmpty() + stackCurrent.getDisplayName());
        int miss = size - result;
        ItemStack extracted = inv.extractItem(i, Math.min(inv.getStackInSlot(i).getCount(), miss), simulate);
        //StorageNetwork.log("extracted " + extracted + "?" + extracted.isEmpty() + extracted.getDisplayName());//for non SDRAWERS this is still the real thing
        world.markChunkDirty(pos, this);
        //the other KEY fix for https://github.com/PrinceOfAmber/Storage-Network/issues/19, where it 
        //voided stuff when you took all from storage drawer: extracted can have a >0 stacksize, but still be air,
        //so the getCount overrides the 16, and gives zero instead, so i di my own override of, if empty then it got all so use source
        result += Math.min(extracted.isEmpty() ? stackCurrent.getCount() : extracted.getCount(), miss);
        res = stackCurrent.copy();
        if (res.isEmpty()) {//workaround for storage drawer and chest thing
          res = extracted.copy();
          res.setCount(result);
        }
        StorageNetwork.log("!TileMaster request: yes actually remove items from source now " + res + "__" + result);
        //  int rest = s.getCount();
        if (result == size) { return ItemHandlerHelper.copyStackWithSize(res, size); }
      }
    }
    if (result == 0) { return ItemStack.EMPTY; }
    return ItemHandlerHelper.copyStackWithSize(res, result);
  }
  @Override
  public void update() {
    if (world == null || world.isRemote) { return; }
    //refresh time in config, default 200 ticks
    try {
      if (storageInventorys == null || connectables == null
          || (world.getTotalWorldTime() % (ConfigHandler.refreshTicks) == 0)) {
        refreshNetwork();
      }
      updateImports();
      updateExports();
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }
  @Override
  public SPacketUpdateTileEntity getUpdatePacket() {
    NBTTagCompound syncData = new NBTTagCompound();
    this.writeToNBT(syncData);
    return new SPacketUpdateTileEntity(this.pos, 1, syncData);
  }
  @Override
  public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
    readFromNBT(pkt.getNbtCompound());
  }
  @Override
  public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newSate) {
    return oldState.getBlock() != newSate.getBlock();
  }
}
