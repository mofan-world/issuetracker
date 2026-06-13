export type HierarchyItem = {
  id: number
  parentId?: number | null
  versionNo: string
  pathLabel: string
}

export type VersionTreeNode<T extends HierarchyItem> = T & {
  children: VersionTreeNode<T>[]
}

export function buildVersionTree<T extends HierarchyItem>(items: T[]): VersionTreeNode<T>[] {
  const nodes = new Map<number, VersionTreeNode<T>>()
  items.forEach((item) => nodes.set(item.id, { ...item, children: [] }))

  const roots: VersionTreeNode<T>[] = []
  nodes.forEach((node) => {
    const parent = node.parentId ? nodes.get(node.parentId) : undefined
    if (parent && parent.id !== node.id) {
      parent.children.push(node)
    } else {
      roots.push(node)
    }
  })

  const sortNodes = (tree: VersionTreeNode<T>[]) => {
    tree.sort((left, right) =>
      left.versionNo.localeCompare(right.versionNo, undefined, { numeric: true, sensitivity: 'base' }),
    )
    tree.forEach((node) => sortNodes(node.children))
  }
  sortNodes(roots)
  return roots
}

export function filterVersionTree<T extends HierarchyItem>(
  tree: VersionTreeNode<T>[],
  predicate: (node: VersionTreeNode<T>) => boolean,
): VersionTreeNode<T>[] {
  return tree.flatMap((node) => {
    const children = filterVersionTree(node.children, predicate)
    return predicate(node) || children.length
      ? [{ ...node, children }]
      : []
  })
}

export function flattenVersionTree<T extends HierarchyItem>(
  tree: VersionTreeNode<T>[],
): VersionTreeNode<T>[] {
  return tree.flatMap((node) => [node, ...flattenVersionTree(node.children)])
}
