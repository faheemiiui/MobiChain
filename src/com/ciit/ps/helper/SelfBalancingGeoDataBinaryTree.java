package com.ciit.ps.helper;
import com.ciit.lp.entities.LocationAuthorityDetails;
import com.grum.geocalc.EarthCalc;
import com.grum.geocalc.Point;

import java.net.InetAddress;
import java.util.Date;
import java.util.Scanner;

class TNodes
{
	TNodes left, right;
	LAData	data;
	double    distanceFromParentNode;
	int        height;

	public TNodes()
	{
		left = null;
		right = null;
		distanceFromParentNode = 0;
		height = 0;
	}

	public TNodes(double n,LAData data)
	{

		left = null;
		right = null;
		distanceFromParentNode = n;
		height = 0;
		this.data = data;
	}
}

class SelfBalancingBinarySearchTrees
{
	private TNodes root;

	public SelfBalancingBinarySearchTrees()
	{
		root = null;
	}

	public boolean isEmpty()
	{
		return root == null;
	}

	public void clear()
	{
		root = null;
	}

	public void insert(LAData data)
	{
		if(!isEmpty() && countNodes()==1)
		{
			// now insert dummy node
			double bearing = EarthCalc.getBearing(data.getLocation(), root.data.getLocation());
			Point dummyPoint = EarthCalc.pointRadialDistance(data.getLocation(), bearing, 20);
			LAData dummy = new LAData(new LocationAuthorityDetails(null, dummyPoint.getLongitude(), dummyPoint.getLatitude(), 0, null, null, null));
			root = insert(dummy, root);
			root = insert(data, root);
		}
		else
		{
			root = insert(data, root);
		}
	}

	private int height(TNodes t)
	{

		return t == null ? -1 : t.height;
	}

	private int max(int lhs, int rhs)
	{
		return lhs > rhs ? lhs : rhs;
	}

	private TNodes insert(LAData newNode, TNodes parentNode)
	{
		double distanceFromParent = 0;
		if (parentNode == null)
		{
			parentNode = new TNodes(distanceFromParent,newNode);
		}
		else if(parentNode!=null)
		{
			distanceFromParent = newNode.distanceFrom(parentNode.data.getLocation());
			if ( distanceFromParent < parentNode.distanceFromParentNode)
			{
				parentNode.left = insert(newNode, parentNode.left);
				parentNode.left.distanceFromParentNode = distanceFromParent;
				if (height(parentNode.left) - height(parentNode.right) == 2)
				{
					if (distanceFromParent < parentNode.left.distanceFromParentNode)
						parentNode = rotateWithLeftChild(parentNode);
					else
						parentNode = doubleWithLeftChild(parentNode);
				}
			} else if (distanceFromParent > parentNode.distanceFromParentNode)
			{
				parentNode.right = insert(newNode, parentNode.right);
				parentNode.right.distanceFromParentNode = distanceFromParent;
				if (height(parentNode.right) - height(parentNode.left) == 2)
					if (distanceFromParent > parentNode.right.distanceFromParentNode)
						parentNode = rotateWithRightChild(parentNode);
					else
						parentNode = doubleWithRightChild(parentNode);
			} 	
		}
		
		parentNode.height = max(height(parentNode.left), height(parentNode.right)) + 1;
		return parentNode;
	}

	private TNodes rotateWithLeftChild(TNodes k2)
	{
		TNodes k1 = k2.left;
		k2.left = k1.right;
		k1.right = k2;
		k2.height = max(height(k2.left), height(k2.right)) + 1;
		k1.height = max(height(k1.left), k2.height) + 1;
		return k1;
	}

	private TNodes rotateWithRightChild(TNodes k1)
	{
		TNodes k2 = k1.right;
		k1.right = k2.left;
		k2.left = k1;
		k1.height = max(height(k1.left), height(k1.right)) + 1;
		k2.height = max(height(k2.right), k1.height) + 1;
		return k2;
	}

	private TNodes doubleWithLeftChild(TNodes k3)
	{
		k3.left = rotateWithRightChild(k3.left);
		return rotateWithLeftChild(k3);
	}

	private TNodes doubleWithRightChild(TNodes k1)
	{
		k1.right = rotateWithLeftChild(k1.right);
		return rotateWithRightChild(k1);
	}

	public int countNodes()
	{
		return countNodes(root);
	}

	private int countNodes(TNodes r)
	{
		if (r == null)
			return 0;
		else
		{
			int l = 1;
			l += countNodes(r.left);
			l += countNodes(r.right);
			return l;
		}
	}

//	public boolean search(double val)
//	{
//		return search(root, val);
//	}
//
//	private boolean search(TNodes r, double val)
//	{
//		boolean found = false;
//		while ((r != null) && !found)
//		{
//			double rval = r.distanceFromParentNode;
//			if (val < rval)
//				r = r.left;
//			else if (val > rval)
//				r = r.right;
//			else
//			{
//				found = true;
//				break;
//			}
//			found = search(r, val);
//		}
//		return found;
//	}

	public void inorder()
	{
		inorder(root);
	}

	private void inorder(TNodes r)
	{
		if (r != null)
		{
			inorder(r.left);
//			System.out.println(r.data.getLocationAuthority() + " ");
			System.out.println(r.distanceFromParentNode + " ");
			inorder(r.right);
		}
	}

	public void preorder()
	{

		preorder(root);
	}

	private void preorder(TNodes r)
	{
		if (r != null)
		{
			System.out.println(r.distanceFromParentNode + " ");
//			System.out.println(r.data.getLocationAuthority() + " ");
			preorder(r.left);
			preorder(r.right);
		}
	}

	public void postorder()
	{
		postorder(root);
	}

	private void postorder(TNodes r)
	{
		if (r != null)
		{
			postorder(r.left);
			postorder(r.right);
			System.out.println(r.distanceFromParentNode + " ");
//			System.out.println(r.data.getLocationAuthority() + " ");
		}
	}
}

public class SelfBalancingGeoDataBinaryTree
{
	public static void main(String[] args)
	{
		Scanner scan = new Scanner(System.in);

		SelfBalancingBinarySearchTrees sbbst = new SelfBalancingBinarySearchTrees();
		System.out.println("Self Balancing Tree\n");

		int N = 4;
		LocationAuthorityDetails laDetails = null;
		LAData data = null;
		String uniqueId = "CIIT_LPS_LA";
		for (int i = 0; i < N; i++)
		{
			uniqueId = uniqueId+i;
			laDetails = new LocationAuthorityDetails(InetAddress.getLoopbackAddress().getHostAddress().toString(), 70.0+(0.1*i), 30.0+(0.1*i) , 3555, new Date(), uniqueId,"");
//			sbbst.insert(scan.nextInt());
			sbbst.insert(new LAData(laDetails));
		}

		System.out.println("\nPre-order  :");
		sbbst.preorder();
		System.out.println("\nIn-order   :");
		sbbst.inorder();
		System.out.println("\nPost-order :");
		sbbst.postorder();
		scan.close();
	}
}