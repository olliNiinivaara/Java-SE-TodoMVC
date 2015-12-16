/*
 * You need to add stremebase_0_7_1.jar or later to build path
 * Get it from https://github.com/olliNiinivaara/Stremebase-source 
 * 
 * 
 * Copyright 2015 Olli Niinivaara
 *
 * Olli Niinivaara licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.stremebase.examples.todomvc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.OptionalLong;
import java.util.function.LongPredicate;
import java.util.stream.LongStream;

import com.stremebase.dal.Table;
import com.stremebase.field.TextField;
import com.stremebase.field.TupleField;


public class Data extends Table
{
  public static final String FILTERALL = "all";
  public static final String FILTERCOMPLETED = "completed";

  TupleField<OptionalLong> COMPLETED = new TupleField<>(this, "COMPLETED", OptionalLong.class);
  TextField TEXT = new TextField(this, "TEXT", null);

  public boolean useFilter;
  public long shownOnlyCompleted;
  final Collection<LongPredicate> filters = new ArrayList<>();
  final LongPredicate filter = key -> !useFilter || (COMPLETED.getAsLong(key, 0)==shownOnlyCompleted);

  public int totalCount;
  public int activeCount;
  public boolean nothingToSee;


  @SuppressWarnings("unchecked")
  public Data(int id, String name)
  {
    super(id, name);
    COMPLETED.autoFlush = true;
    TEXT.autoFlush = true;
    filters.add(filter);

    COMPLETED.arrayMap.keys().forEach(key ->
    {
      totalCount++;
      if (COMPLETED.getAsLong(key, 0)==0) activeCount++;
    });

    nothingToSee = totalCount==0;
  }

  public ArrayList<Long> getItems()
  {
    LongStream s = query(null, filters, null, Integer.MAX_VALUE).getResult();
    return s.collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
  }

  public void createItem(String text)
  {
    long key = COMPLETED.getFreeKey();
    COMPLETED.set(key, OptionalLong.of(0));
    TEXT.set(key, text);
    totalCount++;
    activeCount++;
    checkVisibility();
  }

  public void saveText(long key, String text)
  {
    TEXT.set(key, text);
  }

  public String getText(long key)
  {
    return TEXT.get(key);
  }

  public boolean isCompleted(long key)
  {
    return ((OptionalLong)COMPLETED.get(key, 0)).getAsLong()==1;
  }

  public void clearCompleted()
  {
    COMPLETED.arrayMap.keys().forEach(key ->
    {
      if (COMPLETED.getAsLong(key, 0)==1)
      {
        totalCount--;
        remove(key);
      }
    });
    checkVisibility();
  }

  public void deleteItem(long key)
  {
    OptionalLong completed = COMPLETED.get(key, 0);
    if (!completed.isPresent()) return;
    totalCount--;
    if (completed.getAsLong()==0) activeCount--;
    remove(key);
    checkVisibility();
  }

  public void toggleStatus(long key)
  {
    OptionalLong completed = COMPLETED.get(key, 0);
    if (!completed.isPresent()) return;
    if (completed.getAsLong()==0)
    {
      activeCount--;
      COMPLETED.set(key, OptionalLong.of(1));
    }
    else
    {
      activeCount++;
      COMPLETED.set(key, OptionalLong.of(0));
    }
    checkVisibility();
  }

  public void filter(String filtertype)
  {
    if (filtertype.equals(FILTERALL)) useFilter = false;
    else
    {
      useFilter = true;
      shownOnlyCompleted = filtertype.equals(FILTERCOMPLETED) ? 1 : 0;
    }
    checkVisibility();
  }

  private void checkVisibility()
  {
    if (!useFilter) nothingToSee = totalCount==0;
    else nothingToSee = (shownOnlyCompleted==1 && totalCount>activeCount) || (shownOnlyCompleted==0 && activeCount>0);
  }
}