package com.pmease.gitop.web.page.project.source.commit.diff;

import java.util.List;

import javax.annotation.Nullable;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.event.IEvent;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.OnDomReadyHeaderItem;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.link.AbstractLink;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.AbstractReadOnlyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;

import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.pmease.gitop.core.Gitop;
import com.pmease.gitop.core.manager.CommitCommentManager;
import com.pmease.gitop.model.CommitComment;
import com.pmease.gitop.model.Project;
import com.pmease.gitop.web.GitopSession;
import com.pmease.gitop.web.component.comment.CommitCommentEditor;
import com.pmease.gitop.web.component.comment.CommitCommentPanel;
import com.pmease.gitop.web.component.comment.event.CommitCommentEvent;
import com.pmease.gitop.web.component.label.AgeLabel;
import com.pmease.gitop.web.component.link.UserAvatarLink;
import com.pmease.gitop.web.git.GitUtils;
import com.pmease.gitop.web.model.CommitCommentModel;
import com.pmease.gitop.web.model.UserModel;
import com.pmease.gitop.web.page.project.source.commit.SourceCommitPage;

@SuppressWarnings("serial")
public class CommentListPanel extends Panel {

	private final IModel<Project> projectModel;
	private final IModel<String> commitModel;
	
	private boolean showAllNotes = false;
	
	public CommentListPanel(String id,
			IModel<Project> projectModel, 
			IModel<String> commitModel, 
			IModel<List<CommitComment>> commentsModel) {
		super(id, commentsModel);
		
		this.projectModel = projectModel;
		this.commitModel = commitModel;
		
		setOutputMarkupId(true);
	}
	
	@Override
	protected void onInitialize() {
		super.onInitialize();
		
		final WebMarkupContainer commentsHolder = new WebMarkupContainer("commentlist");
		commentsHolder.setOutputMarkupId(true);
		add(commentsHolder);
		IModel<List<CommitComment>> commentsModel = new LoadableDetachableModel<List<CommitComment>>() {

			@Override
			protected List<CommitComment> load() {
				if (showAllNotes) {
					return getCommitComments();
				} else {
					return getCommentsOnCommit();
				}
			}
		};
		
		commentsHolder.add(new ListView<CommitComment>("comments", commentsModel) {

			@Override
			protected void populateItem(ListItem<CommitComment> item) {
				CommitComment c = item.getModelObject();
				item.add(new UserAvatarLink("author", 
											new UserModel(c.getAuthor()),
											UserAvatarLink.Mode.AVATAR_ONLY));
				item.add(new CommitCommentPanel("message", projectModel, new CommitCommentModel(c)) {
					@Override
					protected Component createCommentHead(String id) {
						
						CommitComment comment = getCommitComment();
						
						Fragment frag = new Fragment(id, "commenthead", CommentListPanel.this);
						frag.add(new UserAvatarLink("author", 
													new UserModel(comment.getAuthor()),
													UserAvatarLink.Mode.NAME_ONLY));
						AbstractLink link = new BookmarkablePageLink<Void>("commitlink", 
								SourceCommitPage.class,
								SourceCommitPage.newParams(getProject(), getCommit()));
						frag.add(link);
						link.add(new Label("sha", GitUtils.abbreviateSHA(getCommit(), 6)));
						frag.add(new AgeLabel("age", Model.of(comment.getCreatedDate())));
						frag.add(newEditLink("editlink"));
						frag.add(newRemoveLink("removelink"));
						
						WebMarkupContainer lineLink = new WebMarkupContainer("linelink");
						lineLink.setVisibilityAllowed(!Strings.isNullOrEmpty(comment.getLine()));
						lineLink.add(AttributeModifier.replace("href", "#" + comment.getLine()));
						lineLink.add(new Label("lineid", getShortLineId(comment.getLine())));
						frag.add(lineLink);
						
						return frag;
					}
				});
			}
		});
		
		WebMarkupContainer formHolder = new WebMarkupContainer("formholder") {
			
			@Override
			protected void onConfigure() {
				super.onConfigure();
				setVisibilityAllowed(GitopSession.getCurrentUser().isPresent());
			}
		};
		
		add(formHolder);
		if (GitopSession.getCurrentUser().isPresent()) {
			formHolder.add(new UserAvatarLink("author", new UserModel(GitopSession.getCurrentUser().get()), UserAvatarLink.Mode.AVATAR_ONLY));
			formHolder.add(new CommitCommentEditor("form") {

				@Override
				protected void onCancel(AjaxRequestTarget target, Form<?> form) {
				}

				@Override
				protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
					CommitComment c = new CommitComment();
					c.setAuthor(GitopSession.getCurrentUser().get());
					c.setCommit(getCommit());
					c.setProject(getProject());
					c.setContent(getCommentText());
					
					clearInput();
					
					Gitop.getInstance(CommitCommentManager.class).save(c);
					onAddComment(target, c);
				}
				
				@Override
				protected Component createCancelButton(String id, Form<?> form) {
					return new WebMarkupContainer(id).setVisibilityAllowed(false);
				}
				
				@Override
				protected IModel<String> getSubmitButtonLabel() {
					return Model.of("Comment on this commit");
				}
				
				@Override
				protected Form<?> createForm(String id) {
					return new Form<Void>(id) {
						@Override
						public void renderHead(IHeaderResponse response) {
							super.renderHead(response);
							
							response.render(OnDomReadyHeaderItem.forScript(
									String.format("gitop.form.areYouSure('#%s');", getMarkupId())));
						}
					};
				}
			});
		} else {
			formHolder.add(new WebMarkupContainer("author").setVisibilityAllowed(false));
			formHolder.add(new WebMarkupContainer("form").setVisibilityAllowed(false));
		}
		
		add(new Label("count", new AbstractReadOnlyModel<Integer>() {

			@Override
			public Integer getObject() {
				return getCommentsOnCommit().size();
			}
			
		}));
		
		add(new Label("inlinecount", new AbstractReadOnlyModel<Integer>() {

			@Override
			public Integer getObject() {
				return getCommentsOnLine().size();
			}
			
		}));
		
		add(new Label("sha", new AbstractReadOnlyModel<String>() {

			@Override
			public String getObject() {
				return GitUtils.abbreviateSHA(getCommit(), 6);
			}
			
		}));
		
		CheckBox check = new CheckBox("showAllTrigger", new PropertyModel<Boolean>(this, "showAllNotes"));
		add(check);
		
		check.add(new AjaxFormComponentUpdatingBehavior("change") {

			@Override
			protected void onUpdate(AjaxRequestTarget target) {
				target.add(commentsHolder);
			}
		});
	}

	private List<CommitComment> getCommentsOnCommit() {
		return Lists.newArrayList(Iterables.filter(getCommitComments(), new Predicate<CommitComment>() {

			@Override
			public boolean apply(CommitComment input) {
				return Strings.isNullOrEmpty(input.getLine());
			}
			
		}));
	}
	
	private List<CommitComment> getCommentsOnLine() {
		return Lists.newArrayList(Iterables.filter(getCommitComments(), new Predicate<CommitComment>() {

			@Override
			public boolean apply(CommitComment input) {
				return !Strings.isNullOrEmpty(input.getLine());
			}
			
		}));
	}
	
	@SuppressWarnings("unchecked")
	private List<CommitComment> getCommitComments() {
		return (List<CommitComment>) getDefaultModelObject();
	}
	
	public @Nullable String getShortLineId(String line) {
		if (Strings.isNullOrEmpty(line)) {
			return null;
		}
		
		int pos = line.indexOf("-");
		String hash = line.substring(0, pos);
		return GitUtils.abbreviateSHA(hash, 6) + line.substring(pos);
	}
	
	private void onAddComment(AjaxRequestTarget target, CommitComment c) {
		target.add(this);
	}
	
	@Override
	public void onEvent(IEvent<?> sink) {
		if (sink.getPayload() instanceof CommitCommentEvent) {
			CommitCommentEvent e = (CommitCommentEvent) sink.getPayload();
			if (showAllNotes || !e.getComment().isLineComment()) {
				e.getTarget().add(this);
			}
		}
	}
	
	private Project getProject() {
		return projectModel.getObject();
	}
	
	private String getCommit() {
		return commitModel.getObject();
	}

	@Override
	public void onDetach() {
		if (projectModel != null) {
			projectModel.detach();
		}
		
		if (commitModel != null) {
			commitModel.detach();
		}
		
		super.onDetach();
	}
}
