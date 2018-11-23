package com.evilco.mc.nbt.tag;

import com.evilco.mc.nbt.error.TagNotFoundException;
import com.evilco.mc.nbt.error.UnexpectedTagTypeException;


import java.util.Map;

/**
 * @auhtor Johannes Donath <johannesd@evil-co.com>
 * @copyright Copyright (C) 2014 Evil-Co <http://www.evil-co.org>
 */
public interface INamedTagContainer extends ITagContainer {

	/**
	 * Returns the tag associated with the given name.
	 * @param name The tag name.
	 */
	public ITag getTag ( String name);
	
	/**
	 * Returns the tag associated with the given name, ensuring its type is as expected
	 * @param name The tag name
	 * @param tagClass The expected tag type
	 * @return the tag
	 * @throws com.evilco.mc.nbt.error.UnexpectedTagTypeException The tag is found, but of different type than expected
	 * @throws com.evilco.mc.nbt.error.TagNotFoundException There is no tag with the given name in this container
	 */
	public <T extends ITag> T getTag(String name, Class<T> tagClass) 
			throws UnexpectedTagTypeException, TagNotFoundException;

	/**
	 * Returns a named map of all tags.
	 * @return
	 */
	public Map<String, ITag> getTags ();

	/**
	 * Removes a tag from the container.
	 * @param tag The tag name.
	 */
	public void removeTag ( String tag);

	/**
	 * Sets a new tag.
	 * @param tag The tag.
	 */
	public void setTag ( ITag tag);
}