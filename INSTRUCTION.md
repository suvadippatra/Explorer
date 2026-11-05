# Explorer Application UI & Architecture Instructions

This document defines the core UI structure, layout terminology, and standard mechanisms across the Explorer application.

---

## 1. Top Layout Hierarchy

### **Title Bar (Tab Bar)**
- **Definition**: The top-most horizontal bar of the workspace window or app tab system.
- **Components**: Displays active document/workspace tabs, tab titles, close buttons (`x`), and the `+` button to create or open a new tab.
- **Positioning**: Fixed at the very top of the window frame.

### **Heading (Action Bar)**
- **Definition**: The secondary control bar positioned **directly below the Title Bar** with zero middle gaps.
- **Components**: Contains the primary navigation control (Triple-line Menu drawer icon or Back Arrow button), Page Title text, Search toggle button, and context-specific action controls.
- **Positioning**: Directly flush with the bottom of the Title Bar / Tab Bar.

---

## 2. File Picker & Folder Selection System Mechanism

Any screen or feature requiring a user to select files, folders, HTML hosting targets, or bucket items **MUST** follow this standardized file picker mechanism (`FullPageFilePicker.kt`):

1. **Header Layout**:
   - Back Arrow button on top left.
   - Concise title text (e.g., "Select Files").
   - Search icon button (toggles a pill-shaped search bar in place of the heading title).
   - Sort dropdown button (Name, Size, Date).

2. **Storage Volume Chips**:
   - Horizontal scrollable filter chips allowing instant navigation between Internal Storage, SD Card, and USB drives.

3. **Multi-Select Bucket System**:
   - Users can select multiple individual files and folders into a staged "Bucket" across different storage locations.
   - Each file or folder item is rendered using Material 3 card/item styling with clear selection badges (filled checkmarks).

4. **Mobile vs Wide Screen Action Bar**:
   - **Mobile / Narrow Layout**: The "Done" or "Confirm Selection" action is anchored at the bottom in a prominent pill-shaped bar or FAB to maximize vertical viewing space.
   - **Tablet / Desktop / Wide Layout**: The "Done" button is rendered in the top heading bar.
