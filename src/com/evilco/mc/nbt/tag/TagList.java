package com.evilco.mc.nbt.tag;

import com.evilco.mc.nbt.error.UnexpectedTagTypeException;
import com.evilco.mc.nbt.stream.NbtInputStream;
import com.evilco.mc.nbt.stream.NbtOutputStream;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @auhtor Johannes Donath <johannesd@evil-co.com>
 * @copyright Copyright (C) 2014 Evil-Co <http://www.evil-co.org>
 */
public class TagList extends AbstractTag implements IAnonymousTagContainer {

	/**
	 * Stores the tag values.
	 */
	protected List<ITag> tagList;

	/**
	 * Constructs a new TagList.
	 */
	public TagList ( String name) {
		super (name);
		this.tagList = new ArrayList<> ();
	}

	/**
	 * Constructs a new TagList.
	 * @param name
	 * @param tagList
	 */
	public TagList ( String name,  List<ITag> tagList) {
		super (name);

		// verify arguments
		Preconditions.checkNotNull (tagList, "tagList");

		// save tagList
		this.tagList = tagList;
	}

	/**
	 * Constructs a new TagList.
	 * @param inputStream
	 * @param anonymous
	 * @throws IOException
	 */
	public TagList ( NbtInputStream inputStream, boolean anonymous) throws IOException {
		super (inputStream, anonymous);

		// create tagList
		this.tagList = new ArrayList<> ();

		// get type ID
		byte type = inputStream.readByte ();

		// get type
		TagType tagType = TagType.valueOf (type);

		// read size
		int size = inputStream.readInt ();

		// (no data, skip)
		if (tagType == TagType.END) return;

		// load all elements
		for (int i = 0; i < size; i++) {
			this.addTag (inputStream.readTag (tagType, true));
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void addTag ( ITag tag) {
		this.tagList.add (tag);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<ITag> getTags () {
		return (new ImmutableList.Builder<ITag> ().addAll (this.tagList)).build ();
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	public <T extends ITag> List<T> getTags(Class<T> tagClass) throws UnexpectedTagTypeException {
		ImmutableList.Builder<T> builder = new ImmutableList.Builder<T>();
		for (ITag tag : tagList) {
			if (!tagClass.isInstance(tag))
				throw new UnexpectedTagTypeException(
						"The list entry should be of type "
								+ tagClass.getSimpleName()
								+ ", but is of type "
								+ tag.getClass().getSimpleName());
			builder.add((T) tag);
		}
		return builder.build ();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public byte getTagID () {
		return TagType.LIST.typeID;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void removeTag ( ITag tag) {
		this.tagList.remove (tag);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setTag (int i,  ITag tag) {
		this.tagList.set (i, tag);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void write (NbtOutputStream outputStream, boolean anonymous) throws IOException {
		super.write (outputStream, anonymous);

		// write type
		outputStream.writeByte ((this.tagList.size () > 0 ? this.tagList.get (0).getTagID () : TagType.END.typeID));

		// write size
		outputStream.writeInt (this.tagList.size ());

		// write tags
		for (ITag tag : this.tagList) {
			// write data
			tag.write (outputStream, true);
		}
	}
}