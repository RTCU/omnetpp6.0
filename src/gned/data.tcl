#==========================================================================
#  DATA.TCL -
#            part of the GNED, the Tcl/Tk graphical topology editor of
#                            OMNeT++
#
#   By Andras Varga
#   Parts: Istvan Pataki, Gabor Vincze ({deity|vinczeg}@sch.bme.hu)
#
#==========================================================================

#----------------------------------------------------------------#
#  Copyright (C) 1992,99 Andras Varga
#  Technical University of Budapest, Dept. of Telecommunications,
#  Stoczek u.2, H-1111 Budapest, Hungary.
#
#  This file is distributed WITHOUT ANY WARRANTY. See the file
#  `license' for details on this and other legal matters.
#----------------------------------------------------------------#


#------------------------------------------------
# Data structure is defined in datadict.tcl
# Initialization of global vars (ned(0,*) and ned(nextkey)) is also
# at the end of datadict.tcl
#------------------------------------------------


# addItem --
#
# create an item with the given type and add it to ned()
#
proc addItem {type parentkey} {
   global ned ddict

   # choose key
   set key $ned(nextkey)
   incr ned(nextkey)

   # add ned() fields
   foreach i [array names ddict "common,*"] {
      regsub -- "common," $i "" field
      set ned($key,$field) $ddict($i)
   }
   foreach i [array names ddict "$type,*"] {
      regsub -- "$type," $i "" field
      set ned($key,$field) $ddict($i)
   }

   # set parent  (everyone has one except root)
   set ned($key,parentkey) $parentkey
   if {$type!="root"} {lappend ned($parentkey,childrenkeys) $key}

   # generate a unique name if necessary
   if [info exist ned($key,name)] {
      set name $ned($key,name)
      set max 0
      set name $ned($key,name)
      foreach i [array names ned "*,name"] {
         if [regsub -- "$name" $ned($i) "" n] {
             catch {if {int($n)>$max} {set max $n}}
         }
      }
      set max [expr int($max+1)]
      set ned($key,name) "$name$max"
   }

   # mark it "not selected" on canvas
   set ned($key,selected) 0

puts "dbg: addItem: $type $key added to $parentkey (its chilren now: $ned($parentkey,childrenkeys))"

   return $key
}


# insertItem --
#
# make item the child of another item
#
proc insertItem {key parentkey} {
    global ned

    set ned($key,parentkey) $parentkey
    lappend ned($parentkey,childrenkeys) $key
}


# deleteItem --
#
# delete an item; also delete it from canvas, with linked items
#
proc deleteItem {key} {
    global ned canvas ddfields

puts "dbg: deleteItem $key entered"

    # prevent race conditions...
    set ned($key,being-deleted) 1

    # delete children recursively
    foreach childkey $ned($key,childrenkeys) {
       deleteItem $childkey
    }

    # delete non-child linked objects
    #   (e.g. connections when a submod is deleted)
    foreach i [array names ned "*,*ownerkey"] {
       if {[info exist ned($i)] && $ned($i)==$key} {
          regsub -- ",.*ownerkey" $i "" childkey
          deleteItem $childkey
       }
    }

    # delete item from canvas (if it's there)
    # if a canvas displayed exactly this item, close that canvas
    set canv_id [canvasIdFromItemKey $key]
    if {$canv_id!=""} {

       if {$canvas($canv_id,module-key)==$key} {
           destroyCanvas $canv_id
       } else {
           set c $canvas($canv_id,canvas)
           foreach i [array names ned "$key,*-cid"] {
              $c delete $ned($i)
           }
       }
    }

    # unlink from parent
    set parentkey $ned($key,parentkey)
    set pos [lsearch -exact $ned($parentkey,childrenkeys) $key]
    set ned($parentkey,childrenkeys) [lreplace $ned($parentkey,childrenkeys) $pos $pos]

    # delete from array
    set type $ned($key,type)
    foreach field $ddfields($type) {
        unset ned($key,$field)
    }
    foreach field $ddfields(common) {
        catch {unset ned($key,$field)}
    }
}


# renameItem --
#
# Rename the item if possible and return the new name.
#
proc renameItem {key name} {
    global ned

    # check
    if ![info exist ned($key,name)] {
       error "item $key has no name attribute"
    }

    # adjust name string if needed
    regsub -all -- {[^a-zA-Z0-9_]} $name {} name
    regsub -- {^[0-9]} $name {_\0} name
    if {$name==""} {
       return $ned($key,name)
    }

    # make name unique
    puts "TBD: renameItem: make name unique"

    # rename
    set ned($key,name) $name

    # FIXME: mark file dirty
    # FIXME: update drawing, tree manager, tabs...
    puts "DBG: renameItem: key='$key' name='$name'"
    puts "DBG: FIXME: update drawing, mark dirty etc!"
    return $name
}


# getChildren --
#
# Find a child element within the given parent
#
proc getChildren {parentkey} {
    global ned
    return $ned($parentkey,childrenkeys)
}

# getChildrenWithType --
#
# Find a child element within the given parent
#
proc getChildrenWithType {parentkey type} {
    global ned

    set keys {}
    foreach key $ned($parentkey,childrenkeys) {
       if {$ned($key,type)==$type} {
           lappend keys $key
       }
    }
    return $keys
}


# canvasIdFromItemKey --
#
# returns the id of canvas the item is on
#
proc canvasIdFromItemKey {key} {
    global ned canvas

    while {$key!=""} {
        foreach i [array names canvas "*,module-key"] {
            if {$canvas($i)==$key} {
                regsub -- ",module-key" $i "" canv_id
                return $canv_id
            }
        }
        set key $ned($key,parentkey)
    }
    return ""
}

# isItemPartOfItem --
#
# check if 1st item is under 2nd one in the data structure tree
#
proc isItemPartOfItem {key anc_key} {
    global ned
    while {$key!=""} {
        if {$key==$anc_key} {
            return 1
        }
        set key $ned($key,parentkey)
    }
    return 0
}

# itemKeyFromName --
#
# get a key from name and type
#
proc itemKeyFromName {name type} {
    global ned

    foreach i [array names ned "*,type"] {
        if {$ned($i)==$type} {
            regsub -- ",type" $i "" key
            if {$ned($key,name)==$name} {
               return $key
            }
        }
    }
    return ""
}

#
# get a key from canvas item id
#
proc itemKeyFromCid {cid {canv_id ""}} {
    global ned gned canvas

    if {$cid==""} {return ""}
    if {$canv_id==""} {set canv_id $gned(canvas_id)}

    foreach i [array names ned "*-cid"] {
       if {$ned($i)==$cid} {
          regsub -- ",.*-cid" $i "" key
          # make sure item is on this canvas
          if [isItemPartOfItem $key $canvas($canv_id,module-key)] {
              return $key
          }
       }
    }
    return ""
}

#
# return connections to/from module or submodule
#
proc connKeysOfItem {key} {
   global ned

   set keys ""
   foreach i [array names ned "*,src-ownerkey"] {
      if {$ned($i)==$key} {
         regsub -- ",src-ownerkey" $i "" connkey
         lappend keys $connkey
      }
   }
   foreach i [array names ned "*,dest-ownerkey"] {
      if {$ned($i)==$key} {
         regsub -- ",dest-ownerkey" $i "" connkey
         lappend keys $connkey
      }
   }
   return $keys
}

#
# change a key in the tmp_ned() array passed by name
#
proc changeKey {tmp_ned_name from to} {
   upvar $tmp_ned_name tmp_ned

   foreach i [array names tmp_ned "$from,*"] {
      regsub -- $from $i $to newi
      set tmp_ned($newi) $tmp_ned($i)
      unset tmp_ned($i)
   }

   foreach i [array names tmp_ned "*,*-ownerkey"] {
      if {$tmp_ned($i)==$from} {set tmp_ned($i) $to}
   }

}


# copyArrayFromNed
#
# Copy items from ned() into another array (the "clipboard")
#
proc copyArrayFromNed {tmp_ned_name keys} {
   global ned
   upvar $tmp_ned_name tmp_ned

   # clear target array
   foreach i [array names tmp_ned] {
      unset tmp_ned($i)
   }

   # copy out all needed elems from ned()
   foreach i [array names ned] {
      regsub -- ",.*" $i "" key
      if {[lsearch $keys $key]!=-1} {
         set tmp_ned($i) $ned($i)
      }
   }

   # null out canvas ids
   foreach i [array names tmp_ned "*-cid"] {
      set tmp_ned($i) ""
   }
}


# pasteArrayIntoNed
#
#  merge an array ("clipboard") similar to ned() into ned()
#  return the list of item keys
#
proc pasteArrayIntoNed {tmp_ned_name} {
   global ned
   upvar $tmp_ned_name tmp_ned

   # collect all keys from tmp_ned()
   set keys {}
   foreach i [array names tmp_ned "*,type"] {
      regsub -- ",type" $i "" key
      if {[lsearch $keys $key]==-1} {
         lappend keys $key
      }
   }

   # alter keys in tmp_ned to avoid conflicts
   foreach i $keys {
      changeKey tmp_ned $i "tmp-$i"
   }

   # pick a new key for each
   set new_keys {}
   foreach key $keys {
      set key "tmp-$key"

      set new_key $ned(nextkey)
      incr ned(nextkey)

      changeKey tmp_ned $key $new_key
      lappend new_keys $new_key
   }

   # merge array into ned()
   foreach i [array names tmp_ned ] {
      set ned($i) $tmp_ned($i)
   }
   return $new_keys
}


# checkArray --
#
# Data structure sanity check, for debugging.
# Check ned() array if it conforms to the data dictionary
#
proc checkArray {} {
   global ned ddict

   # check that ned() contains only those attrs in ddict()
   foreach i [array names ned] {
      if {$i=="nextkey"} continue
      regsub -- ",.*" $i "" key
      regsub -- ".*," $i "" attr
      if {![info exist ned($key,type)]} {
         puts "ned($i)=$ned($i): invalid item, has no 'type' attribute"
         continue
      }
      set type $ned($key,type)
      if {![info exist ddict($type,$attr)]} {
          puts "ned($key/$type): unwanted attribute '$attr'"
      }
   }

   # check that ned() contains all elements in ddict()
   foreach i [array names ned "*,type"] {
      regsub -- ",.*" $i "" key
      set type $ned($key,type)
      foreach j [array names ddict "$type,*"] {
         regsub -- "$type," $j "" attr
         if {![info exist ned($key,$attr)]} {
            puts "ned($key/$type): missing attribute '$attr'"
         }
      }
   }

   # check parent-child relationships
   foreach i [array names ned "*,parentkey"] {
      regsub -- ",.*" $i "" key
      if {![info exist ned($key,type)]} {
         puts "ned($i)=$ned($i): invalid item, has no 'type' attribute"
         continue
      }
      set type $ned($key,type)
      set ownerkey $ned($i)
      if {![info exist ned($ownerkey,type)]} {
         puts "ned($key/$type): parent '$ownerkey' not in array"
      } else {
         set parenttype $ned($ownerkey,type)
         set possibleparents $ddict($ned($key,type)-parents)
         if {[lsearch $possibleparents $parenttype]==-1} {
            puts "ned($key/$type): parent type '$parenttype' not allowed"
            puts "   (allowed ones: $possibleparents)"
         }
      }
   }
}


proc markNedfileOfItemDirty {key} {
    global ned

    # seach up until nedfile item
    while {$ned($key,type)!="nedfile"} {
        set key $ned($key,parentkey)
    }

    # mark nedfile dirty
    if {$ned($key,dirty)==0} {
        set ned($key,dirty) 1
        updateTreeManager
    }
}


proc nedfileIsDirty {key} {
    global ned

    if {$ned($key,type)!="nedfile"} {error "internal error in nedfileIsDirty"}

    return $ned($key,dirty)
}


proc nedfileClearDirty {key} {
    global ned

    if {$ned($key,type)!="nedfile"} {error "internal error in nedfileIsDirty"}

    set ned($key,dirty) 0

    updateTreeManager
}


proc deleteNedfile {nedfilekey} {
    global ned

    # guard against openUnnamedCanvas (called from destroyCanvas)
    set ned($nedfilekey,being-deleted) 1

    # close all related canvases
    foreach key $ned($nedfilekey,childrenkeys) {
        set ned($key,being-deleted) 1
        set canv_id [canvasIdFromItemKey $key]
        if {$canv_id!=""} {
            destroyCanvas $canv_id
        }
    }
    set canv_id [canvasIdFromItemKey $nedfilekey]
    if {$canv_id!=""} {
        destroyCanvas $canv_id
    }

    # delete from parent (root)
    set root $ned($nedfilekey,parentkey)
    set pos [lsearch -exact $ned($root,childrenkeys) $nedfilekey]
    set ned($root,childrenkeys) [lreplace $ned($root,childrenkeys) $pos $pos]

    # delete from array
    deleteModuleData $nedfilekey
}


# deleteModuleData --
#
# used by switchToGraphics and deleteNedfile.
#
# $key must be a top-level item (module,simple,channel etc.).
# This proc is a simplified version of deleteItem:
#  o  deleting graphics stuff from the canvas is omitted
#     (callers are supposed to clear the whole canvas manually)
#  o  recursive deletion of data is also simplified
#     (we don't need to care about deleting 'related items' (like
#     ned(*,*-ownerkey)) because such relations do not occur between
#     top-level items)
#
proc deleteModuleData {key} {
    global ned ddfields

    # delete children recursively
    foreach childkey $ned($key,childrenkeys) {
        deleteModuleData $childkey
    }

    # - deleting non-child linked objects omitted
    # - deleting item from canvas is omitted
    # - potentially closing the canvas is omitted
    # - unlink from parent omitted

    # delete from array
    set type $ned($key,type)
    foreach field $ddfields($type) {
        unset ned($key,$field)
    }
    foreach field $ddfields(common) {
        catch {unset ned($key,$field)}
    }
}


